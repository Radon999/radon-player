/* Bit-perfect straight to a USB DAC, around Android's sound system: the music's own samples streamed to the DAC's
 * isochronous endpoint with the kernel's usbfs (the way USB Audio Player Pro does it), on phones whose Android
 * mixes everything first (no AudioMixerAttributes bit-perfect mode). The Kotlin side (UsbDirect) finds the DAC, claims
 * its streaming interface, picks the format and sets the sample rate; this file only keeps the samples flowing:
 * a ring buffer the player writes into, a few URBs always queued on the DAC's endpoint, and the DAC's feedback
 * endpoint (asynchronous DACs say how fast they really want samples) steering how many frames go in each packet. */
#include <jni.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <time.h>
#include <sys/resource.h>
#include <sys/syscall.h>

static long long nowMs(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return (long long)t.tv_sec * 1000 + t.tv_nsec / 1000000; }

#define NURB 64          /* data URBs kept queued, 1 ms each (as Linux's own USB audio driver sends them): 64 ms ahead, so a
                          * moment the phone is busy elsewhere never leaves the DAC without music (a split-second gap) */
#define NFB 2            /* feedback URBs kept queued */

typedef struct {
    struct usbdevfs_urb *urb;
    uint8_t *buf;
    int realFrames;      /* frames of music (not silence) in it */
    int gen;             /* the flush generation it was filled in */
} Slot;

typedef struct {
    int fd, ep, fbEp, maxPacket, fbMaxPacket, frameBytes, pktsPerUrb, fbScale, hs;
    uint32_t nominal;    /* frames per packet, Q16.16 */
    _Atomic uint32_t fpp;
    uint32_t acc;
    uint8_t *ring; size_t cap, head, tail, count;
    pthread_mutex_t mu;
    _Atomic long long played;   /* music frames the DAC has taken since the last flush */
    _Atomic int inflight;       /* music frames queued in URBs */
    _Atomic int running, paused, muted, gen, error;
    pthread_t th;
    Slot slots[NURB];
    struct usbdevfs_urb *fb[NFB];
    uint8_t *fbBuf[NFB];
    /* What happened, for the app's report (Problems): feedback packets, ones taken, the last one's bytes; data URBs
     * and packets the DAC or the kernel said went wrong */
    _Atomic int fbSeen, fbTaken, urbErr, pktErr, urbs;
    _Atomic uint32_t fbLast; _Atomic int fbLastLen;
    /* When a data URB and a feedback packet last came back (ms, monotonic), and how often the stream was kicked */
    _Atomic long long dataAt, fbAt, startedAt; _Atomic int kicks;
    /* URBs handed to the kernel and not yet back: every one must come back before the stream's memory is freed, or
     * the next stream on the same DAC got them back instead (and the kernel wrote into freed memory: the crash after
     * skipping many songs, and starting the DJ, when there were more URBs than the old clean-up waited for) */
    _Atomic int outstanding;
} Stream;


/* Live streams: every call with a handle checks it's still alive under a shared lock, and stop takes it out under the
 * exclusive one before freeing it, so a call that comes late (the watch, a report, a player skipping songs quickly)
 * finds nothing instead of freed memory (that crashed the app when songs were skipped fast). */
#define NLIVE 32
static pthread_rwlock_t g_live = PTHREAD_RWLOCK_INITIALIZER;
static Stream *g_streams[NLIVE];
static int alive(Stream *s) { if (!s) return 0; for (int i = 0; i < NLIVE; i++) if (g_streams[i] == s) return 1; return 0; }

static size_t ringTake(Stream *s, uint8_t *dst, size_t want) {
    size_t n = want < s->count ? want : s->count;
    size_t first = s->cap - s->tail; if (first > n) first = n;
    memcpy(dst, s->ring + s->tail, first);
    memcpy(dst + first, s->ring, n - first);
    s->tail = (s->tail + n) % s->cap; s->count -= n;
    return n;
}

/* Fills a data URB: packet by packet, the frames the DAC wants now (the accumulator carries the fraction). */
static void fill(Stream *s, Slot *sl) {
    struct usbdevfs_urb *u = sl->urb;
    int off = 0, real = 0;
    uint32_t fpp = atomic_load(&s->fpp);
    int maxFrames = s->maxPacket / s->frameBytes;
    pthread_mutex_lock(&s->mu);
    int pause = atomic_load(&s->paused);
    for (int i = 0; i < s->pktsPerUrb; i++) {
        uint32_t a = s->acc + fpp;
        int frames = (int)(a >> 16); s->acc = a & 0xFFFF;
        if (frames > maxFrames) frames = maxFrames;
        int bytes = frames * s->frameBytes;
        size_t got = 0;
        if (!pause) got = ringTake(s, sl->buf + off, (size_t)bytes);
        got -= got % (size_t)s->frameBytes;
        if (got < (size_t)bytes) memset(sl->buf + off + got, 0, (size_t)bytes - got);   /* silence */
        if (atomic_load(&s->muted) && got) memset(sl->buf + off, 0, got);
        real += (int)got / s->frameBytes;
        u->iso_frame_desc[i].length = (unsigned)bytes;
        u->iso_frame_desc[i].actual_length = 0;
        u->iso_frame_desc[i].status = 0;
        off += bytes;
    }
    sl->gen = atomic_load(&s->gen);
    pthread_mutex_unlock(&s->mu);
    sl->realFrames = real;
    atomic_fetch_add(&s->inflight, real);
    u->type = USBDEVFS_URB_TYPE_ISO;
    u->endpoint = (unsigned char)s->ep;
    u->flags = USBDEVFS_URB_ISO_ASAP;
    u->buffer = sl->buf;
    u->buffer_length = off;
    u->number_of_packets = s->pktsPerUrb;
    u->actual_length = 0; u->status = 0; u->error_count = 0; u->start_frame = 0;
}

static void submitFb(Stream *s, int i) {
    struct usbdevfs_urb *u = s->fb[i];
    memset(u, 0, sizeof(*u) + sizeof(struct usbdevfs_iso_packet_desc));
    u->type = USBDEVFS_URB_TYPE_ISO;
    u->endpoint = (unsigned char)s->fbEp;
    u->flags = USBDEVFS_URB_ISO_ASAP;
    u->buffer = s->fbBuf[i];
    u->buffer_length = s->fbMaxPacket;
    u->number_of_packets = 1;
    u->iso_frame_desc[0].length = (unsigned)s->fbMaxPacket;
    u->usercontext = (void *)(intptr_t)(1000 + i);
    if (ioctl(s->fd, USBDEVFS_SUBMITURB, u) < 0) s->fbEp = 0;   /* no feedback then: nominal rate */
    else atomic_fetch_add(&s->outstanding, 1);
}

/* The DAC's feedback: how many frames it wants per packet. Full speed sends 10.14 in 3 bytes, high speed 16.16 in 4,
 * but devices vary, so whichever reading lands near the nominal rate is taken. */
static void feedback(Stream *s, const uint8_t *b, int len) {
    if (len < 3) return;
    uint32_t v3 = (uint32_t)b[0] | ((uint32_t)b[1] << 8) | ((uint32_t)b[2] << 16);
    uint32_t v4 = len >= 4 ? (v3 | ((uint32_t)b[3] << 24)) : 0;
    atomic_fetch_add(&s->fbSeen, 1); atomic_store(&s->fbLast, len >= 4 ? v4 : v3); atomic_store(&s->fbLastLen, len);
    uint32_t cands[3] = { v4, v3 << 2, v3 };
    for (int i = 0; i < 3; i++) {
        uint64_t f = (uint64_t)cands[i] * (uint32_t)s->fbScale;   /* per feedback period -> per data packet */
        if (f > (uint64_t)s->nominal * 9 / 10 && f < (uint64_t)s->nominal * 11 / 10) { atomic_store(&s->fpp, (uint32_t)f); atomic_fetch_add(&s->fbTaken, 1); return; }
    }
}

static void *loop(void *arg) {
    Stream *s = (Stream *)arg;
    /* Audio priority (Android's THREAD_PRIORITY_URGENT_AUDIO, as its own audio threads have): the DAC is kept fed
     * even when the phone is busy; where it isn't allowed, the normal priority it had */
    setpriority(PRIO_PROCESS, (id_t)syscall(SYS_gettid), -19);
    for (int i = 0; i < NURB; i++) {
        fill(s, &s->slots[i]);
        s->slots[i].urb->usercontext = (void *)(intptr_t)i;
        if (ioctl(s->fd, USBDEVFS_SUBMITURB, s->slots[i].urb) < 0) { atomic_store(&s->error, errno); atomic_store(&s->running, 0); }
        else atomic_fetch_add(&s->outstanding, 1);
    }
    if (s->fbEp) for (int i = 0; i < NFB; i++) submitFb(s, i);
    while (atomic_load(&s->running)) {
        struct usbdevfs_urb *done = NULL;
        if (ioctl(s->fd, USBDEVFS_REAPURB, &done) < 0) {
            if (errno == EINTR) continue;
            atomic_store(&s->error, errno); break;
        }
        /* Which of this stream's URBs it is, by its address (never read from one that isn't ours) */
        int id = -1;
        for (int i = 0; i < NURB && id < 0; i++) if (done == s->slots[i].urb) id = i;
        for (int i = 0; i < NFB && id < 0; i++) if (done == s->fb[i]) id = 1000 + i;
        if (id < 0) continue;   /* not ours (an old stream's, long gone): left alone */
        atomic_fetch_sub(&s->outstanding, 1);
        if (id >= 1000) {
            int i = id - 1000;
            atomic_store(&s->fbAt, nowMs());
            if (done->status == 0 && done->iso_frame_desc[0].actual_length > 0)
                feedback(s, s->fbBuf[i], (int)done->iso_frame_desc[0].actual_length);
            if (atomic_load(&s->running) && s->fbEp) submitFb(s, i);
            continue;
        }
        Slot *sl = &s->slots[id];
        atomic_fetch_add(&s->urbs, 1);
        atomic_store(&s->dataAt, nowMs());
        if (done->status != 0) atomic_fetch_add(&s->urbErr, 1);
        for (int k = 0; k < done->number_of_packets; k++) if (done->iso_frame_desc[k].status != 0) { atomic_fetch_add(&s->pktErr, 1); break; }
        atomic_fetch_sub(&s->inflight, sl->realFrames);
        if (sl->gen == atomic_load(&s->gen)) atomic_fetch_add(&s->played, sl->realFrames);
        if (done->status == -ENODEV || done->status == -ESHUTDOWN) { atomic_store(&s->error, ENODEV); break; }
        if (!atomic_load(&s->running)) continue;
        fill(s, sl);
        if (ioctl(s->fd, USBDEVFS_SUBMITURB, sl->urb) < 0) { atomic_store(&s->error, errno); break; }
        atomic_fetch_add(&s->outstanding, 1);
    }
    atomic_store(&s->running, 0);
    return NULL;
}

JNIEXPORT jlong JNICALL Java_org_omarchy_music_UsbNative_start(JNIEnv *env, jclass c, jint fd, jint ep, jint maxPacket,
        jint pktsPerSec, jint fbEp, jint fbMaxPacket, jint fbScale, jint frameBytes, jint rate, jint bufferMs) {
    Stream *s = calloc(1, sizeof(Stream));
    if (!s) return 0;
    s->fd = fd; s->ep = ep; s->maxPacket = maxPacket; s->fbEp = fbEp; s->fbMaxPacket = fbMaxPacket > 0 ? fbMaxPacket : 4;
    s->fbScale = fbScale > 0 ? fbScale : 1; s->frameBytes = frameBytes;
    /* 1 ms a URB (8 packets at high speed), as Linux's own USB audio driver sends: the 8 ms ones (64 packets) stopped
     * going out about 1.6 s in on a Samsung phone's USB controller (the FiiO KA17), though its feedback kept coming */
    s->pktsPerUrb = pktsPerSec / 1000; if (s->pktsPerUrb < 1) s->pktsPerUrb = 1; if (s->pktsPerUrb > 64) s->pktsPerUrb = 64;
    s->nominal = (uint32_t)(((uint64_t)rate << 16) / (uint64_t)pktsPerSec);
    atomic_store(&s->fpp, s->nominal);
    s->cap = (size_t)rate * (size_t)frameBytes * (size_t)bufferMs / 1000; s->cap -= s->cap % (size_t)frameBytes;
    s->ring = malloc(s->cap);
    pthread_mutex_init(&s->mu, NULL);
    for (int i = 0; i < NURB; i++) {
        s->slots[i].urb = calloc(1, sizeof(struct usbdevfs_urb) + (size_t)s->pktsPerUrb * sizeof(struct usbdevfs_iso_packet_desc));
        s->slots[i].buf = malloc((size_t)maxPacket * (size_t)s->pktsPerUrb);
    }
    for (int i = 0; i < NFB; i++) {
        s->fb[i] = calloc(1, sizeof(struct usbdevfs_urb) + sizeof(struct usbdevfs_iso_packet_desc));
        s->fbBuf[i] = calloc(1, (size_t)s->fbMaxPacket + 8);
    }
    atomic_store(&s->running, 1);
    atomic_store(&s->startedAt, nowMs()); atomic_store(&s->dataAt, nowMs()); atomic_store(&s->fbAt, nowMs());
    if (pthread_create(&s->th, NULL, loop, s) != 0) { free(s->ring); free(s); return 0; }
    pthread_rwlock_wrlock(&g_live);
    int put = 0;
    for (int i = 0; i < NLIVE && !put; i++) if (!g_streams[i]) { g_streams[i] = s; put = 1; }
    pthread_rwlock_unlock(&g_live);
    if (!put) { atomic_store(&s->running, 0); for (int i = 0; i < NURB; i++) ioctl(s->fd, USBDEVFS_DISCARDURB, s->slots[i].urb);
        pthread_join(s->th, NULL); return 0; }   /* (never: a few streams at most) */
    return (jlong)(intptr_t)s;
}

/* Copies as much of the buffer as fits (whole frames); the bytes taken. */
static jint impl_write(JNIEnv *env, jclass c, jlong h, jobject buf, jint off, jint len) {
    Stream *s = (Stream *)(intptr_t)h;
    if (!s || !atomic_load(&s->running)) return -1;
    uint8_t *src = (uint8_t *)(*env)->GetDirectBufferAddress(env, buf);
    if (!src) return -1;
    pthread_mutex_lock(&s->mu);
    size_t room = s->cap - s->count;
    size_t n = (size_t)len < room ? (size_t)len : room;
    n -= n % (size_t)s->frameBytes;
    size_t first = s->cap - s->head; if (first > n) first = n;
    memcpy(s->ring + s->head, src + off, first);
    memcpy(s->ring, src + off + first, n - first);
    s->head = (s->head + n) % s->cap; s->count += n;
    pthread_mutex_unlock(&s->mu);
    return (jint)n;
}

static jlong impl_played(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; return s ? atomic_load(&s->played) : 0;
}

/* Music frames not heard yet: in the ring and in queued URBs. */
static jlong impl_pending(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; if (!s) return 0;
    pthread_mutex_lock(&s->mu); size_t n = s->count; pthread_mutex_unlock(&s->mu);
    return (jlong)(n / (size_t)s->frameBytes) + atomic_load(&s->inflight);
}

/* For the report: URBs done, URBs and packets gone wrong, feedback packets seen and taken, the last feedback (raw), its
 * length, frames per packet now (Q16.16) and nominal. */
static jlongArray impl_stats(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h;
    jlong v[14] = {0};
    if (s) { v[0] = atomic_load(&s->urbs); v[1] = atomic_load(&s->urbErr); v[2] = atomic_load(&s->pktErr); v[3] = atomic_load(&s->fbSeen);
        v[4] = atomic_load(&s->fbTaken); v[5] = atomic_load(&s->fbLast); v[6] = atomic_load(&s->fbLastLen); v[7] = atomic_load(&s->fpp); v[8] = s->nominal;
        long long n = nowMs();
        v[9] = n - atomic_load(&s->dataAt); v[10] = n - atomic_load(&s->fbAt); v[11] = atomic_load(&s->played);
        v[12] = atomic_load(&s->kicks); v[13] = n - atomic_load(&s->startedAt); }
    jlongArray out = (*env)->NewLongArray(env, 14);
    if (out) (*env)->SetLongArrayRegion(env, out, 0, 14, v);
    return out;
}

static jint impl_error(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; if (!s) return ENODEV;
    return atomic_load(&s->error) ? atomic_load(&s->error) : (atomic_load(&s->running) ? 0 : EPIPE);
}

/* The DAC stopped taking music (no data URB back for a while, its feedback still coming): the queued URBs are
 * cancelled; the loop gets them back and sends them again, scheduled afresh. */
/* The music in the ring not sent yet (for a stream set up again: it goes on in the new one, nothing skipped). */
static jbyteArray impl_unsent(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; if (!s) return NULL;
    pthread_mutex_lock(&s->mu);
    size_t n = s->count;
    jbyteArray out = (*env)->NewByteArray(env, (jsize)n);
    if (out && n) {
        size_t first = s->cap - s->tail; if (first > n) first = n;
        (*env)->SetByteArrayRegion(env, out, 0, (jsize)first, (const jbyte *)(s->ring + s->tail));
        if (n > first) (*env)->SetByteArrayRegion(env, out, (jsize)first, (jsize)(n - first), (const jbyte *)s->ring);
    }
    pthread_mutex_unlock(&s->mu);
    return out;
}

static void impl_kick(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; if (!s || !atomic_load(&s->running)) return;
    atomic_fetch_add(&s->kicks, 1);
    atomic_store(&s->dataAt, nowMs());
    for (int i = 0; i < NURB; i++) ioctl(s->fd, USBDEVFS_DISCARDURB, s->slots[i].urb);
}

static void impl_setPaused(JNIEnv *env, jclass c, jlong h, jboolean p) {
    Stream *s = (Stream *)(intptr_t)h; if (s) atomic_store(&s->paused, p ? 1 : 0);
}

static void impl_setMuted(JNIEnv *env, jclass c, jlong h, jboolean m) {
    Stream *s = (Stream *)(intptr_t)h; if (s) atomic_store(&s->muted, m ? 1 : 0);
}

/* Throws away what's waiting (a seek, a new song): the queued URBs finish as they are but no longer count. */
static void impl_flush(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; if (!s) return;
    pthread_mutex_lock(&s->mu);
    s->head = s->tail = s->count = 0;
    atomic_fetch_add(&s->gen, 1);
    atomic_store(&s->played, 0);
    pthread_mutex_unlock(&s->mu);
}

JNIEXPORT void JNICALL Java_org_omarchy_music_UsbNative_stop(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; if (!s) return;
    pthread_rwlock_wrlock(&g_live);
    int was = 0;
    for (int i = 0; i < NLIVE; i++) if (g_streams[i] == s) { g_streams[i] = NULL; was = 1; }
    pthread_rwlock_unlock(&g_live);
    if (!was) return;   /* stopped already */
    atomic_store(&s->running, 0);
    for (int i = 0; i < NURB; i++) ioctl(s->fd, USBDEVFS_DISCARDURB, s->slots[i].urb);
    for (int i = 0; i < NFB; i++) ioctl(s->fd, USBDEVFS_DISCARDURB, s->fb[i]);
    pthread_join(s->th, NULL);
    /* Every URB still out collected (they come back cancelled), so nothing points at freed memory; at most a second */
    for (int k = 0; k < 400 && atomic_load(&s->outstanding) > 0; k++) {
        struct usbdevfs_urb *u = NULL;
        if (ioctl(s->fd, USBDEVFS_REAPURBNDELAY, &u) == 0 && u) {
            int mine = 0;
            for (int i = 0; i < NURB && !mine; i++) if (u == s->slots[i].urb) mine = 1;
            for (int i = 0; i < NFB && !mine; i++) if (u == s->fb[i]) mine = 1;
            if (mine) atomic_fetch_sub(&s->outstanding, 1);
        } else if (errno == EAGAIN) usleep(2500);
        else if (errno == ENODEV) break;   /* the DAC's gone: the kernel let them all go */
        else usleep(2500);
    }
    /* Any still out after that (never seen): their memory kept rather than freed under the kernel */
    if (atomic_load(&s->outstanding) > 0) return;
    for (int i = 0; i < NURB; i++) { free(s->slots[i].urb); free(s->slots[i].buf); }
    for (int i = 0; i < NFB; i++) { free(s->fb[i]); free(s->fbBuf[i]); }
    pthread_mutex_destroy(&s->mu);
    free(s->ring); free(s);
}

/* Gives an interface back to Android's own USB audio driver once we let it go. */
JNIEXPORT jint JNICALL Java_org_omarchy_music_UsbNative_reattach(JNIEnv *env, jclass c, jint fd, jint ifno) {
    struct usbdevfs_ioctl cmd = { .ifno = ifno, .ioctl_code = USBDEVFS_CONNECT, .data = NULL };
    return ioctl(fd, USBDEVFS_IOCTL, &cmd);
}

/* The calls Java makes with a handle: only on a live stream (see g_live). */
JNIEXPORT jint JNICALL Java_org_omarchy_music_UsbNative_write(JNIEnv *env, jclass c, jlong h, jobject buf, jint off, jint len) {
    pthread_rwlock_rdlock(&g_live);
    jint r = alive((Stream *)(intptr_t)h) ? impl_write(env, c, h, buf, off, len) : -1;
    pthread_rwlock_unlock(&g_live);
    return r;
}

JNIEXPORT jlong JNICALL Java_org_omarchy_music_UsbNative_played(JNIEnv *env, jclass c, jlong h) {
    pthread_rwlock_rdlock(&g_live);
    jlong r = alive((Stream *)(intptr_t)h) ? impl_played(env, c, h) : 0;
    pthread_rwlock_unlock(&g_live);
    return r;
}

JNIEXPORT jlong JNICALL Java_org_omarchy_music_UsbNative_pending(JNIEnv *env, jclass c, jlong h) {
    pthread_rwlock_rdlock(&g_live);
    jlong r = alive((Stream *)(intptr_t)h) ? impl_pending(env, c, h) : 0;
    pthread_rwlock_unlock(&g_live);
    return r;
}

JNIEXPORT jlongArray JNICALL Java_org_omarchy_music_UsbNative_stats(JNIEnv *env, jclass c, jlong h) {
    pthread_rwlock_rdlock(&g_live);
    jlongArray r = alive((Stream *)(intptr_t)h) ? impl_stats(env, c, h) : NULL;
    pthread_rwlock_unlock(&g_live);
    return r;
}

JNIEXPORT jint JNICALL Java_org_omarchy_music_UsbNative_error(JNIEnv *env, jclass c, jlong h) {
    pthread_rwlock_rdlock(&g_live);
    jint r = alive((Stream *)(intptr_t)h) ? impl_error(env, c, h) : 19;
    pthread_rwlock_unlock(&g_live);
    return r;
}

JNIEXPORT jbyteArray JNICALL Java_org_omarchy_music_UsbNative_unsent(JNIEnv *env, jclass c, jlong h) {
    pthread_rwlock_rdlock(&g_live);
    jbyteArray r = alive((Stream *)(intptr_t)h) ? impl_unsent(env, c, h) : NULL;
    pthread_rwlock_unlock(&g_live);
    return r;
}

JNIEXPORT void JNICALL Java_org_omarchy_music_UsbNative_kick(JNIEnv *env, jclass c, jlong h) {
    pthread_rwlock_rdlock(&g_live);
    if (alive((Stream *)(intptr_t)h)) impl_kick(env, c, h);
    pthread_rwlock_unlock(&g_live);
}

JNIEXPORT void JNICALL Java_org_omarchy_music_UsbNative_setPaused(JNIEnv *env, jclass c, jlong h, jboolean p) {
    pthread_rwlock_rdlock(&g_live);
    if (alive((Stream *)(intptr_t)h)) impl_setPaused(env, c, h, p);
    pthread_rwlock_unlock(&g_live);
}

JNIEXPORT void JNICALL Java_org_omarchy_music_UsbNative_setMuted(JNIEnv *env, jclass c, jlong h, jboolean m) {
    pthread_rwlock_rdlock(&g_live);
    if (alive((Stream *)(intptr_t)h)) impl_setMuted(env, c, h, m);
    pthread_rwlock_unlock(&g_live);
}

JNIEXPORT void JNICALL Java_org_omarchy_music_UsbNative_flush(JNIEnv *env, jclass c, jlong h) {
    pthread_rwlock_rdlock(&g_live);
    if (alive((Stream *)(intptr_t)h)) impl_flush(env, c, h);
    pthread_rwlock_unlock(&g_live);
}


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

#define NURB 8           /* data URBs kept queued */
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
} Stream;

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
}

/* The DAC's feedback: how many frames it wants per packet. Full speed sends 10.14 in 3 bytes, high speed 16.16 in 4,
 * but devices vary, so whichever reading lands near the nominal rate is taken. */
static void feedback(Stream *s, const uint8_t *b, int len) {
    if (len < 3) return;
    uint32_t v3 = (uint32_t)b[0] | ((uint32_t)b[1] << 8) | ((uint32_t)b[2] << 16);
    uint32_t v4 = len >= 4 ? (v3 | ((uint32_t)b[3] << 24)) : 0;
    uint32_t cands[3] = { v4, v3 << 2, v3 };
    for (int i = 0; i < 3; i++) {
        uint64_t f = (uint64_t)cands[i] * (uint32_t)s->fbScale;   /* per feedback period -> per data packet */
        if (f > (uint64_t)s->nominal * 9 / 10 && f < (uint64_t)s->nominal * 11 / 10) { atomic_store(&s->fpp, (uint32_t)f); return; }
    }
}

static void *loop(void *arg) {
    Stream *s = (Stream *)arg;
    for (int i = 0; i < NURB; i++) {
        fill(s, &s->slots[i]);
        s->slots[i].urb->usercontext = (void *)(intptr_t)i;
        if (ioctl(s->fd, USBDEVFS_SUBMITURB, s->slots[i].urb) < 0) { atomic_store(&s->error, errno); atomic_store(&s->running, 0); }
    }
    if (s->fbEp) for (int i = 0; i < NFB; i++) submitFb(s, i);
    while (atomic_load(&s->running)) {
        struct usbdevfs_urb *done = NULL;
        if (ioctl(s->fd, USBDEVFS_REAPURB, &done) < 0) {
            if (errno == EINTR) continue;
            atomic_store(&s->error, errno); break;
        }
        intptr_t id = (intptr_t)done->usercontext;
        if (id >= 1000) {
            int i = (int)(id - 1000);
            if (done->status == 0 && done->iso_frame_desc[0].actual_length > 0)
                feedback(s, s->fbBuf[i], (int)done->iso_frame_desc[0].actual_length);
            if (atomic_load(&s->running) && s->fbEp) submitFb(s, i);
            continue;
        }
        Slot *sl = &s->slots[id];
        atomic_fetch_sub(&s->inflight, sl->realFrames);
        if (sl->gen == atomic_load(&s->gen)) atomic_fetch_add(&s->played, sl->realFrames);
        if (done->status == -ENODEV || done->status == -ESHUTDOWN) { atomic_store(&s->error, ENODEV); break; }
        if (!atomic_load(&s->running)) continue;
        fill(s, sl);
        if (ioctl(s->fd, USBDEVFS_SUBMITURB, sl->urb) < 0) { atomic_store(&s->error, errno); break; }
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
    s->pktsPerUrb = pktsPerSec / 125; if (s->pktsPerUrb < 1) s->pktsPerUrb = 1; if (s->pktsPerUrb > 64) s->pktsPerUrb = 64;   /* 8 ms a URB */
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
    if (pthread_create(&s->th, NULL, loop, s) != 0) { free(s->ring); free(s); return 0; }
    return (jlong)(intptr_t)s;
}

/* Copies as much of the buffer as fits (whole frames); the bytes taken. */
JNIEXPORT jint JNICALL Java_org_omarchy_music_UsbNative_write(JNIEnv *env, jclass c, jlong h, jobject buf, jint off, jint len) {
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

JNIEXPORT jlong JNICALL Java_org_omarchy_music_UsbNative_played(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; return s ? atomic_load(&s->played) : 0;
}

/* Music frames not heard yet: in the ring and in queued URBs. */
JNIEXPORT jlong JNICALL Java_org_omarchy_music_UsbNative_pending(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; if (!s) return 0;
    pthread_mutex_lock(&s->mu); size_t n = s->count; pthread_mutex_unlock(&s->mu);
    return (jlong)(n / (size_t)s->frameBytes) + atomic_load(&s->inflight);
}

JNIEXPORT jint JNICALL Java_org_omarchy_music_UsbNative_error(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; if (!s) return ENODEV;
    return atomic_load(&s->error) ? atomic_load(&s->error) : (atomic_load(&s->running) ? 0 : EPIPE);
}

JNIEXPORT void JNICALL Java_org_omarchy_music_UsbNative_setPaused(JNIEnv *env, jclass c, jlong h, jboolean p) {
    Stream *s = (Stream *)(intptr_t)h; if (s) atomic_store(&s->paused, p ? 1 : 0);
}

JNIEXPORT void JNICALL Java_org_omarchy_music_UsbNative_setMuted(JNIEnv *env, jclass c, jlong h, jboolean m) {
    Stream *s = (Stream *)(intptr_t)h; if (s) atomic_store(&s->muted, m ? 1 : 0);
}

/* Throws away what's waiting (a seek, a new song): the queued URBs finish as they are but no longer count. */
JNIEXPORT void JNICALL Java_org_omarchy_music_UsbNative_flush(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; if (!s) return;
    pthread_mutex_lock(&s->mu);
    s->head = s->tail = s->count = 0;
    atomic_fetch_add(&s->gen, 1);
    atomic_store(&s->played, 0);
    pthread_mutex_unlock(&s->mu);
}

JNIEXPORT void JNICALL Java_org_omarchy_music_UsbNative_stop(JNIEnv *env, jclass c, jlong h) {
    Stream *s = (Stream *)(intptr_t)h; if (!s) return;
    atomic_store(&s->running, 0);
    for (int i = 0; i < NURB; i++) ioctl(s->fd, USBDEVFS_DISCARDURB, s->slots[i].urb);
    for (int i = 0; i < NFB; i++) ioctl(s->fd, USBDEVFS_DISCARDURB, s->fb[i]);
    pthread_join(s->th, NULL);
    struct usbdevfs_urb *u;   /* collect what's left so nothing points at freed memory */
    for (int k = 0; k < 64 && ioctl(s->fd, USBDEVFS_REAPURBNDELAY, &u) == 0; k++) {}
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

package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

/** The desktop's side of a media player on Linux (MPRIS), as the desktop names it. */
@DBusInterfaceName("org.mpris.MediaPlayer2")
interface MprisRoot : DBusInterface {
    fun Raise()
    fun Quit()
}

/** Its buttons: what a media key, a headset or the desktop's own "now playing" asks for. */
@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
interface MprisPlayer : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(Offset: Long)
    fun SetPosition(TrackId: DBusPath, Position: Long)
    fun OpenUri(Uri: String)
}

/** Linux: the keyboard's and a headset's play/pause, next and previous keys, and the desktop's own "now playing"
 *  (GNOME's and KDE's media controls, the lock screen), as Windows has them through MediaKeys and MediaSession. On
 *  Linux both are one thing, MPRIS: the app announces itself as a media player on the session's D-Bus, says what's
 *  playing, and the desktop sends the keys to it. Without a session bus (no desktop) nothing happens. */
object Mpris {
    const val NAME = "org.mpris.MediaPlayer2.radonplayer"
    const val PATH = "/org/mpris/MediaPlayer2"
    private const val ROOT = "org.mpris.MediaPlayer2"
    private const val PLAYER = "org.mpris.MediaPlayer2.Player"

    @Volatile private var conn: DBusConnection? = null
    @Volatile private var now = MprisRules.Now()

    fun start() {
        if (!System.getProperty("os.name").lowercase().contains("linux") || System.getenv("OMARCHY_MUSIC_MPRIS") == "0") return
        Thread({
            runCatching {
                val c = DBusConnectionBuilder.forSessionBus().build()
                c.exportObject(PATH, Service)
                c.requestBusName(NAME)
                conn = c
                Runtime.getRuntime().addShutdownHook(Thread { runCatching { c.disconnect() } })
                watch()
            }.onFailure { logWarn("media keys (no session bus?)", it) }
        }, "mpris").apply { isDaemon = true }.start()
    }

    /** What's playing, looked at twice a second on the app's own thread: the desktop is told when it changes. */
    private fun watch() {
        Music.scope.launch(Dispatchers.Main) {
            var at = System.nanoTime()
            while (true) {
                val t = Music.current()
                val after = MprisRules.Now(t?.id ?: "", t?.title ?: "", t?.artist ?: "", t?.album ?: "", t?.thumb ?: "",
                    hasSong = t != null, playing = t != null && !Music.paused, position = Music.livePosition(),
                    duration = Music.duration.takeIf { it > 0 } ?: (t?.duration ?: 0.0))
                val before = now
                val elapsed = (System.nanoTime() - at) / 1e9; at = System.nanoTime()
                now = after
                val changed = MprisRules.changed(before, after)
                if (changed.isNotEmpty() || MprisRules.jumped(before, after, elapsed)) {
                    val seeked = changed.isEmpty()
                    kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { tell(changed, after, seeked) } }
                }
                delay(500)
            }
        }
    }

    private fun tell(changed: Set<String>, n: MprisRules.Now, seeked: Boolean) {
        val c = conn ?: return
        val all = playerProps(n)
        // A change of song or of playing and paused carries where the song is too, so the desktop's clock is right
        val what = if (seeked) mapOf("Position" to all.getValue("Position")) else changed.associateWith { all.getValue(it) } + ("Position" to all.getValue("Position"))
        c.sendMessage(Properties.PropertiesChanged(PATH, PLAYER, what, emptyList()))
    }

    private fun metadata(n: MprisRules.Now): Map<String, Variant<*>> {
        val m = LinkedHashMap<String, Variant<*>>()
        m["mpris:trackid"] = Variant(DBusPath(MprisRules.trackPath(n.id)))
        if (!n.hasSong) return m
        if (n.duration > 0) m["mpris:length"] = Variant(MprisRules.micros(n.duration))
        m["xesam:title"] = Variant(n.title)
        m["xesam:artist"] = Variant(MprisRules.artists(n.artist), "as")
        if (n.album.isNotEmpty()) m["xesam:album"] = Variant(n.album)
        MprisRules.artUrl(n.art).takeIf { it.isNotEmpty() }?.let { m["mpris:artUrl"] = Variant(it) }
        return m
    }

    private fun playerProps(n: MprisRules.Now): Map<String, Variant<*>> = linkedMapOf(
        "PlaybackStatus" to Variant(MprisRules.status(n)),
        "Rate" to Variant(1.0), "MinimumRate" to Variant(1.0), "MaximumRate" to Variant(1.0), "Volume" to Variant(1.0),
        "Metadata" to Variant(metadata(n), "a{sv}"),
        "Position" to Variant(MprisRules.micros(n.position)),
        "CanGoNext" to Variant(true), "CanGoPrevious" to Variant(true), "CanPlay" to Variant(n.hasSong), "CanPause" to Variant(n.hasSong),
        "CanSeek" to Variant(n.hasSong), "CanControl" to Variant(true))

    private fun rootProps(): Map<String, Variant<*>> = linkedMapOf(
        "CanQuit" to Variant(false), "CanRaise" to Variant(false), "HasTrackList" to Variant(false),
        "Identity" to Variant("Radon Player"), "DesktopEntry" to Variant("radon-player"),
        "SupportedUriSchemes" to Variant(listOf("file"), "as"), "SupportedMimeTypes" to Variant(listOf("audio/flac", "audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav"), "as"))

    private fun props(iface: String): Map<String, Variant<*>> = when (iface) {
        ROOT -> rootProps()
        PLAYER -> playerProps(now)
        else -> emptyMap()
    }

    /** A button, done on the app's own thread (the desktop asks from its own). */
    private fun ui(what: () -> Unit) { Music.scope.launch(Dispatchers.Main) { what() } }

    object Service : MprisRoot, MprisPlayer, Properties {
        override fun getObjectPath(): String = PATH
        override fun isRemote(): Boolean = false

        override fun Raise() {}
        override fun Quit() {}

        override fun Next() = ui { Music.sessionNext() }
        override fun Previous() = ui { Music.previous() }
        override fun Pause() = ui { if (!Music.paused) Music.togglePause() }
        override fun PlayPause() = ui { Music.togglePause() }
        override fun Stop() = ui { if (!Music.paused) Music.togglePause() }
        override fun Play() = ui { if (Music.paused) Music.togglePause() }
        override fun Seek(Offset: Long) = ui { Music.seek(MprisRules.seekTo(Music.livePosition(), Offset, Music.duration)) }
        override fun SetPosition(TrackId: DBusPath, Position: Long) = ui {
            // Only for the song that is playing (the desktop may ask a moment late, for the one before)
            if (TrackId.path == MprisRules.trackPath(Music.current()?.id ?: "")) Music.seek(MprisRules.seconds(Position))
        }
        override fun OpenUri(Uri: String) {}

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(iface: String, prop: String): A =
            (props(iface)[prop] ?: throw DBusExecutionException("No such property: $iface.$prop")) as A
        override fun <A : Any?> Set(iface: String, prop: String, value: A) {}
        override fun GetAll(iface: String): Map<String, Variant<*>> = props(iface)
    }
}

package org.omarchy.music

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

/** Radon Player in the car (Android Auto, and any car or app that browses media apps): Up next, your
 *  favorites, playlists, recently played and your music to pick from; play, pause, previous, next and
 *  seeking; Favorite and Shuffle on the car's screen; and each song's cover. Picking a song plays the
 *  list it's in from there, as in the app. */
object Car {
    private const val ROOT = "root"
    val FAVORITE = SessionCommand("radon.favorite", Bundle.EMPTY)
    val SHUFFLE = SessionCommand("radon.shuffle", Bundle.EMPTY)
    val REPEAT = SessionCommand("radon.repeat", Bundle.EMPTY)

    private fun folder(id: String, title: String, playable: Boolean = false) = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(playable)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()

    /** A song as the car shows it; its id says which list it's in and where (list:i). */
    private fun song(list: String, i: Int, t: Track): MediaItem {
        val art = Music.lib.items[t.id]?.art?.takeIf { it.isNotEmpty() && File(it).exists() }?.let { Uri.fromFile(File(it)) }
            ?: t.thumb.takeIf { it.isNotEmpty() }?.let { Uri.parse(sizedThumb(it, 544)) }
        return MediaItem.Builder().setMediaId("$list:$i")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album)
                .setArtworkUri(art).setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build())
            .build()
    }

    /** The songs of a list the car can open. */
    private fun tracks(list: String): List<Track> = when {
        list == "queue" -> Music.queue
        list == "favorites" -> Music.favorites.tracks()
        list == "recent" -> Music.plays.recent()
        list == "mine" -> Music.mySongs().sortedBy { norm(it.title) }
        list.startsWith("pl.") -> Music.lists.get(list.removePrefix("pl."))?.tracks ?: emptyList()
        else -> emptyList()
    }

    private fun children(parent: String): List<MediaItem> = when (parent) {
        ROOT -> listOf(folder("queue", "Up next"), folder("favorites", "Favorites"), folder("playlists", "Playlists"),
            folder("recent", "Recently played"), folder("mine", "Your music"))
        "playlists" -> Music.lists.all().filter { it.tracks.isNotEmpty() }.map { folder("pl.${it.id}", it.name, playable = true) }
        else -> tracks(parent).take(500).mapIndexed { i, t -> song(parent, i, t) }
    }

    /** Whether a media id is one of the car's (list:i, or a playlist to play from its start). */
    fun isCarItem(id: String) = id.startsWith("queue:") || id.startsWith("favorites:") || id.startsWith("recent:") ||
        id.startsWith("mine:") || id.startsWith("pl.")

    /** The car picked an item: play its list from it (on the app's thread). */
    fun play(id: String) {
        val list = id.substringBeforeLast(':', id)
        val i = if (':' in id) id.substringAfterLast(':').toIntOrNull() ?: 0 else 0
        val ts = tracks(list)
        if (ts.isEmpty()) return
        if (list == "queue") Music.playIndex(i.coerceIn(0, ts.size - 1)) else Music.playFrom(ts, i.coerceIn(0, ts.size - 1), false)
    }

    /** Shuffle, Repeat (off, all, one) and Favorite beside the usual buttons: in the notification and on the lock
     *  screen (Android shows the first two there, beside previous, play and next) and on the car's screen. */
    fun buttons(): ImmutableList<CommandButton> {
        val fav = Music.current()?.let { Music.favorites.has(it) } == true
        val rep = Music.repeat
        return ImmutableList.of(
            CommandButton.Builder().setIconResId(R.drawable.car_shuffle).setDisplayName("Shuffle").setSessionCommand(SHUFFLE).build(),
            CommandButton.Builder().setIconResId(when (rep) { "one" -> R.drawable.car_repeat_one; "all" -> R.drawable.car_repeat; else -> R.drawable.car_repeat_off })
                .setDisplayName(when (rep) { "one" -> "Repeat one"; "all" -> "Repeat all"; else -> "Repeat off" }).setSessionCommand(REPEAT).build(),
            CommandButton.Builder().setIconResId(if (fav) R.drawable.car_star else R.drawable.car_star_line)
                .setDisplayName(if (fav) "Unfavorite" else "Favorite").setSessionCommand(FAVORITE).build())
    }

    private const val GEARHEAD = "com.google.android.projection.gearhead"   // Android Auto

    val callback = object : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (controller.packageName == GEARHEAD) { Outputs.carProjecting = true; Music.outputChanged() }
            val base = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(base.availableSessionCommands.buildUpon().add(FAVORITE).add(SHUFFLE).add(REPEAT).build())
                .setAvailablePlayerCommands(base.availablePlayerCommands)
                .setCustomLayout(buttons()).build()
        }

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            if (controller.packageName == GEARHEAD) { Outputs.carProjecting = Outputs.projecting(Music.ctx); Music.outputChanged() }
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, command: SessionCommand,
                                     args: Bundle): ListenableFuture<SessionResult> {
            when (command.customAction) {
                FAVORITE.customAction -> Music.current()?.let { Music.setFavorite(listOf(it)) }
                SHUFFLE.customAction -> Music.shuffle()
                REPEAT.customAction -> Music.cycleRepeat()
            }
            session.setCustomLayout(buttons())
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                                      params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Radon Player"), params))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
                                   page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val all = children(parentId)
            val from = (page * pageSize).coerceAtMost(all.size)
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, minOf(all.size, from + pageSize))), params))
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                               mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            val list = mediaId.substringBeforeLast(':', mediaId)
            val i = mediaId.substringAfterLast(':', "").toIntOrNull()
            val item = if (i != null) tracks(list).getOrNull(i)?.let { song(list, i, it) } else folder(mediaId, mediaId)
            return Futures.immediateFuture(if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
        }

        // The car's pick comes back as these items; the player (see MusicService) plays them through Music.
        override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
                                     items: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(items)
    }
}

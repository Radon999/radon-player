package org.omarchy.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import org.omarchy.music.Music

/** Cast…: the Chromecasts, Google speakers and TVs on your Wi-Fi; tap one to play there, or "Play on this phone".
 *  The desktop's Cast dialog. */
@Composable
fun CastPicker(ui: Ui) {
    val ctx = LocalContext.current
    val router = remember { runCatching { MediaRouter.getInstance(ctx) }.getOrNull() }
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }
    var routes by remember { mutableStateOf(listOf<MediaRouter.RouteInfo>()) }
    fun refresh() {
        routes = router?.routes.orEmpty().filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
    }
    DisposableEffect(router) {
        val cb = object : MediaRouter.Callback() {
            override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(r: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        }
        router?.addCallback(selector, cb, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
        onDispose { router?.removeCallback(cb) }
    }
    val casting = Music.cast != null
    AlertDialog(onDismissRequest = { ui.castPick = false }, containerColor = C.light,
        title = { Text("Play on", style = T.head) },
        text = {
            Column {
                Text(when {
                    router == null -> "Casting isn't available on this phone (it needs Google Play services)."
                    routes.isEmpty() -> "Looking for Chromecasts, Google speakers and TVs on your Wi-Fi…"
                    else -> "Your songs play from this phone over Wi-Fi; the equalizer and crossfeed stay on the phone."
                }, style = T.sub)
                Spacer(Modifier.padding(4.dp))
                Row(Modifier.fillMaxWidth().clickable {
                    router?.unselect(MediaRouter.UNSELECT_REASON_STOPPED); ui.castPick = false
                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.HEADPHONES, if (!casting) C.accent else C.fg, 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("This phone", style = T.title)
                }
                routes.forEach { r ->
                    val on = r.isSelected
                    Row(Modifier.fillMaxWidth().clickable { r.select(); ui.castPick = false }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ico.CAST, if (on) C.accent else C.fg, 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val d = r.description
                            if (!d.isNullOrBlank()) Text(d, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ ui.castPick = false }) { Text("Close", color = C.accent) } })
}

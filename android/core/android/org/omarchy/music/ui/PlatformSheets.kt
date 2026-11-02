package org.omarchy.music.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.omarchy.music.DESKTOP_UA
import org.omarchy.music.Music

// The phone's sheets that are Android's own: the sign-in page, removing other apps' files.

/** A sign-in page in the app; done(url) once isDone says the sign-in finished, done(null) if closed. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLogin(url: String, isDone: (String) -> Boolean, desktop: Boolean = false, done: (String?) -> Unit) {
    var finished by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { done(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(C.bg)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sign in", style = T.title, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Text("✕", style = T.title, modifier = Modifier.clickable { done(null) }.padding(10.dp))
            }
            AndroidView(factory = { ctx ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Google's sign-in takes the WebView as it is; a borrowed browser name gives it away.
                    if (desktop) settings.userAgentString = DESKTOP_UA
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        fun check(u: String): Boolean {
                            if (!finished && isDone(u)) { finished = true; CookieManager.getInstance().flush(); done(u); return true }
                            return false
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest) = check(req.url.toString())
                        override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) { check(u) }
                        override fun onPageFinished(view: WebView, u: String) { check(u) }
                    }
                    loadUrl(url)
                }
            }, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Removing files that aren't the app's own: Android asks you first (trash: into its trash, else for good); done(true)
 *  once you said yes. Call the returned function with the files' addresses. */
@Composable
fun rememberFileRemoval(trash: Boolean, done: (Boolean) -> Unit): (List<String>) -> Unit {
    val l = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        done(r.resultCode == android.app.Activity.RESULT_OK)
    }
    return remember(l) { { uris ->
        val cr = Music.ctx.contentResolver
        val us = uris.map { android.net.Uri.parse(it) }
        val req = if (trash) android.provider.MediaStore.createTrashRequest(cr, us, true) else android.provider.MediaStore.createDeleteRequest(cr, us)
        l.launch(androidx.activity.result.IntentSenderRequest.Builder(req.intentSender).build())
    } }
}

/** Android 11 and up asks before another app's files go. */
val canRemoveFiles: Boolean get() = android.os.Build.VERSION.SDK_INT >= 30

@Composable
fun screenHeightDp(): Int = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp

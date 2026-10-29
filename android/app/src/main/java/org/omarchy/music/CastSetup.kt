package org.omarchy.music

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

/** Cast: Google's own receiver on the TV or speaker plays the songs (no app of ours on it). Our media notification
 *  stays the one notification: Cast's own is turned off. Named in the manifest. */
class CastSetup : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .setCastMediaOptions(CastMediaOptions.Builder().setNotificationOptions(null).build())
        .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

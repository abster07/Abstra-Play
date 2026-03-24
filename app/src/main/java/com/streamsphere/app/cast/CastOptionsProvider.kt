package com.streamsphere.app.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.CastMediaControlIntent

/**
 * The Cast SDK discovers this class automatically via the
 * <meta-data android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME">
 * entry in AndroidManifest.xml.
 *
 * Replace DEFAULT_MEDIA_RECEIVER_APPLICATION_ID with your own Cast Application ID
 * if you register a custom receiver at https://cast.google.com/publish.
 * Using the default receiver works out-of-the-box for HLS/DASH/MP4 streams.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        val mediaOptions = CastMediaOptions.Builder()
            // We do not launch a separate ExpandedControlsActivity — players are
            // handled inside DetailScreen.
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(
                com.google.android.gms.cast.framework.CastMediaControlIntent
                    .DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            )
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

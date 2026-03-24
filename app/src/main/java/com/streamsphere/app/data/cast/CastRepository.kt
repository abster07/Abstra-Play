package com.streamsphere.app.data.cast

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ChromecastState(
    val isAvailable: Boolean = false,   // at least one Cast device visible
    val isCasting: Boolean = false,
    val deviceName: String? = null,
    val errorMessage: String? = null
)

@Singleton
class CastRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CastRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(ChromecastState())
    val state: StateFlow<ChromecastState> = _state.asStateFlow()

    // Lazily resolved — CastContext requires a UI thread call and Play Services.
    private val castContext: CastContext? by lazy {
        try {
            CastContext.getSharedInstance(context)
        } catch (e: Exception) {
            Log.w(TAG, "Cast not available: ${e.message}")
            null
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.d(TAG, "Cast session started: ${session.castDevice?.friendlyName}")
            _state.value = _state.value.copy(
                isCasting  = true,
                deviceName = session.castDevice?.friendlyName,
                errorMessage = null
            )
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.d(TAG, "Cast session ended, error=$error")
            _state.value = _state.value.copy(isCasting = false, deviceName = null)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _state.value = _state.value.copy(
                isCasting  = true,
                deviceName = session.castDevice?.friendlyName
            )
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _state.value = _state.value.copy(isCasting = false)
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _state.value = _state.value.copy(
                errorMessage = "Failed to start Cast session (error $error)"
            )
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
    }

    /** Register the session listener. Call from Activity.onResume(). */
    fun registerSessionListener() {
        scope.launch {
            castContext?.sessionManager?.addSessionManagerListener(
                sessionListener, CastSession::class.java
            )
            updateAvailability()
        }
    }

    /** Unregister the session listener. Call from Activity.onPause(). */
    fun unregisterSessionListener() {
        scope.launch {
            castContext?.sessionManager?.removeSessionManagerListener(
                sessionListener, CastSession::class.java
            )
        }
    }

    /**
     * Load [streamUrl] on the currently connected Chromecast.
     * Returns true if the load request was dispatched successfully.
     */
    fun loadMedia(streamUrl: String, title: String, logoUrl: String?): Boolean {
        val session = castContext?.sessionManager?.currentCastSession ?: run {
            Log.w(TAG, "No active Cast session")
            _state.value = _state.value.copy(errorMessage = "No active Chromecast session.")
            return false
        }

        val remoteClient = session.remoteMediaClient ?: run {
            Log.w(TAG, "remoteMediaClient is null")
            return false
        }

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }

        // Determine stream MIME type heuristically.
        val mimeType = when {
            streamUrl.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
            streamUrl.contains(".mpd",  ignoreCase = true) -> "application/dash+xml"
            streamUrl.contains(".mp4",  ignoreCase = true) -> "video/mp4"
            streamUrl.contains(".ts",   ignoreCase = true) -> "video/mp2t"
            else -> "video/mp4"
        }

        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(mimeType)
            .setMetadata(metadata)
            .build()

        val loadRequest = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        remoteClient.load(loadRequest)
            .addStatusListener { status ->
                if (status.isSuccess) {
                    Log.d(TAG, "Load success")
                } else {
                    Log.e(TAG, "Load failed: $status")
                    scope.launch {
                        _state.value = _state.value.copy(
                            errorMessage = "Chromecast load failed. Check that the stream URL is reachable."
                        )
                    }
                }
            }
        return true
    }

    /** Stop playback on the active Cast session. */
    fun stopCast() {
        val session = castContext?.sessionManager?.currentCastSession ?: return
        session.remoteMediaClient?.stop()
        castContext?.sessionManager?.endCurrentSession(true)
        _state.value = _state.value.copy(isCasting = false, deviceName = null)
    }

    /** True when there is an active, connected CastSession. */
    val isConnected: Boolean
        get() = castContext?.sessionManager?.currentCastSession?.isConnected == true

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun updateAvailability() {
        // Availability is managed by the MediaRouter framework; here we just
        // sync the current session state at startup.
        val session = castContext?.sessionManager?.currentCastSession
        if (session?.isConnected == true) {
            _state.value = _state.value.copy(
                isAvailable = true,
                isCasting   = true,
                deviceName  = session.castDevice?.friendlyName
            )
        }
    }
}

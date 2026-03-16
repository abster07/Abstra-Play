package com.streamsphere.app

import android.app.Application
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StreamSphereApp : Application() {

    private var multicastLock: WifiManager.MulticastLock? = null
    override fun onCreate() {
        super.onCreate()
        acquireMulticastLock()
    }
    private fun acquireMulticastLock() {
        try {
            val wifi = getSystemService(WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("StreamSphere_DLNA").apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d("StreamSphereApp", "Multicast lock acquired")
        } catch (e: Exception) {
            Log.e("StreamSphereApp", "Failed to acquire multicast lock: ${e.message}")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        multicastLock?.takeIf { it.isHeld }?.release()
    }
}

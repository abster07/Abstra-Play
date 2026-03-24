# ── Google Cast SDK ────────────────────────────────────────────────────────────
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.cast.**

# Required: keep the CastOptionsProvider so the Cast framework can find it.
-keep class com.streamsphere.app.cast.CastOptionsProvider { *; }

# ── jUPnP / DLNA ───────────────────────────────────────────────────────────────
-keep class org.jupnp.** { *; }
-dontwarn org.jupnp.**
-dontwarn org.slf4j.**

# ── ExoPlayer / Media3 ─────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Retain custom FFMPEG decoder renderer from the local AAR.
-keep class com.google.android.exoplayer2.ext.ffmpeg.** { *; }

# ── Room ───────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ── Gson / Retrofit ────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.streamsphere.app.data.model.** { *; }

# ── Hilt ───────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**


# Specifically protect the service that is throwing the NPE
-keep class androidx.room.MultiInstanceInvalidationService { *; }
-keep class androidx.room.IMultiInstanceInvalidationService { *; }

# Also keep your specific ViewModel so Hilt can inject it properly in Release
-keep class com.streamsphere.app.viewmodel.** { *; }

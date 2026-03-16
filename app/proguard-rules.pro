-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

-keep class com.streamsphere.app.data.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

-dontwarn androidx.glance.**

-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**



-keep class org.jupnp.** { *; }
-keep interface org.jupnp.** { *; }
-dontwarn org.jupnp.**
-dontwarn org.slf4j.**

# Keep DIDL model classes used by ContentDirectory browsing
-keep class org.jupnp.support.model.** { *; }
-keep class org.jupnp.support.contentdirectory.** { *; }
-keep class org.jupnp.support.avtransport.** { *; }
-keep class org.jupnp.support.renderingcontrol.** { *; }


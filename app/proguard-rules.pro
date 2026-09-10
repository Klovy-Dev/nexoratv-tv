# Moshi / Retrofit
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep class **JsonAdapter { *; }
-keepnames @com.squareup.moshi.JsonClass class *
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Modèles réseau NexoraTV (sérialisés par Moshi via réflexion de secours)
-keep class fr.nexoratv.tv.core.xtream.dto.** { *; }

# Media3 / nextlib FFmpeg (chargé par JNI)
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class io.github.anilbeesetti.nextlib.** { *; }

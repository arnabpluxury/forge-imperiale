# Forge Imperiale R8 / ProGuard Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Gson serialization models (Backup/Restore + MongoDB sync documents)
-keep class com.forge.imperiale.data.backup.** { *; }
-keep class com.forge.imperiale.data.remote.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Room entities
-keep class com.forge.imperiale.data.local.entity.** { *; }

# android-youtube-player JS bridge
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
-keepclassmembers class com.pierfrancescosoffritti.androidyoutubeplayer.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Media3
-dontwarn androidx.media3.**

##############################################
# 🔒 GENERAL SAFE RULES
##############################################

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep constructors (reflection safety)
-keepclassmembers class * {
    public <init>(...);
}

# Don't warn common issues
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-keep class androidx.core.app.CoreComponentFactory { *; }


##############################################
# 🌐 WEBVIEW (CRITICAL FOR STREAMING)
##############################################

# Keep JavaScript interface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Prevent WebView warnings
-dontwarn android.webkit.**


##############################################
# 🎨 JETPACK COMPOSE
##############################################

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**


##############################################
# 📡 RETROFIT / OKHTTP
##############################################

-keepattributes Signature
-keepattributes *Annotation*

-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

-dontwarn retrofit2.**
-dontwarn okhttp3.**


##############################################
# 🧠 GSON / JSON PARSING
##############################################

-keep class com.google.gson.** { *; }

-keep class * {
    @com.google.gson.annotations.SerializedName <fields>;
}


##############################################
# 🗄 ROOM DATABASE (if used)
##############################################

-keep class androidx.room.** { *; }
-keep @androidx.room.* class * { *; }


##############################################
# 🎥 EXOPLAYER (ONLY IF YOU USE IT)
##############################################

-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**


##############################################
# ⚙️ MODEL CLASSES (IMPORTANT)
##############################################

# Keep your app models (adjust package if needed)
-keep class com.kiduyuk.klausk.kiduyutv.model.** { *; }


##############################################
# ⚙️ JNA / LAZYSODIUM (FIX FOR UnsatisfiedLinkError)
##############################################

-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Library { public *; }
-keep class com.goterl.lazysodium.** { *; }
-keep class com.github.joshjdevl.libsodiumjni.** { *; }
-dontwarn com.sun.jna.**

##############################################
# 🚫 OPTIONAL OPTIMIZATION CONTROL
##############################################

# Prevent overly aggressive optimization (safer for streaming apps)
-optimizations !code/simplification/arithmetic
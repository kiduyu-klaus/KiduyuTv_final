##############################################
# 📢 GOOGLE ADS / ADMOB
##############################################

# Google Mobile Ads SDK
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# User Messaging Platform (UMP / GDPR consent)
-keep class com.google.android.ump.** { *; }

# AdMob mediation adapters (if added later)
-keep class com.google.android.gms.ads.mediation.** { *; }


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
-optimizations !code/simplification/arithmetic##############################################
# 🤖 GEMINI AI SDK
##############################################

# Keep Gemini AI SDK classes
-keep class com.google.ai.client.generativeai.** { *; }

# Keep GenerativeModel and related classes
-keep class com.google.ai.client.generativeai.GenerativeModel { *; }
-keep class com.google.ai.client.generativeai.type.** { *; }
-keep class com.google.ai.client.generativeai.java.** { *; }
-keep class com.google.ai.client.generativeai.kotlin.** { *; }

# Keep content and response classes
-keep class com.google.ai.client.generativeai.model.** { *; }
-keep class com.google.ai.client.generativeai.api.** { *; }

# Prevent warnings for AI SDK dependencies
-dontwarn com.google.ai.client.generativeai.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.api.**
-dontwarn com.google.errorprone.**
-dontwarn org.conscrypt.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**

# Keep AI response and request classes
-keep class com.google.ai.client.generativeai.service.** { *; }
-keep class com.google.ai.client.generativeai.exception.** { *; }

# Keep Kotlin coroutines support for AI SDK
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
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
# 🔐 GOOGLE TINK / GOOGLE API CLIENT
##############################################

# Keep Tink cryptography classes
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }

# Keep Google API Client (required by Tink's KeysDownloader)
-keep class com.google.api.client.** { *; }
-keep class com.google.api.client.http.** { *; }
-keep class com.google.api.client.http.javanet.** { *; }
-keepclassmembers class com.google.api.client.** { *; }

# Keep Joda Time (used by Google API Client)
-keep class org.joda.time.** { *; }
-keep class org.joda.money.** { *; }
-keepclassmembers class org.joda.time.** { *; }

-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**


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
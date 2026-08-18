# Project specific ProGuard / R8 rules

# --- Moshi ---
-dontwarn com.squareup.moshi.**
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonClass <fields>;
}
-keep class * extends com.squareup.moshi.JsonAdapter {
    public <init>(...);
}
-keep class * implements com.squareup.moshi.JsonAdapter {
    public <init>(...);
}

# --- Retrofit & OkHttp ---
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- AndroidX Room ---
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase

# --- ML Kit ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- ZXing ---
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- AndroidX Security Crypto ---
-keep class androidx.security.crypto.** { *; }

# --- Data Models ---
-keep class io.github.iokkai.ocularnode.data.** { *; }

# --- WebRTC ---
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class io.getstream.webrtc.android.** { *; }
-dontwarn io.getstream.webrtc.android.**


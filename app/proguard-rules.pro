# ==============================================================================
# PROJECT SPECIFIC PROGUARD RULES
# ==============================================================================

# Preserving line numbers and source file names for cleaner de-obfuscated crash logs
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod,InnerClasses
-keepattributes Exceptions

# ==============================================================================
# APP DATA LAYER — KEEP EVERYTHING (models, repositories, network, crypto, DI)
# ==============================================================================

# Keep the ENTIRE data package (remote models, crypto, repositories, local entities, etc.)
-keep class com.football.livescores.matchlive.data.** { *; }
-keepclassmembers class com.football.livescores.matchlive.data.** { *; }

# Keep DI modules (Hilt providers, interceptors, qualifiers)
-keep class com.football.livescores.matchlive.di.** { *; }
-keepclassmembers class com.football.livescores.matchlive.di.** { *; }

# Keep utils (SharePreferenceUtils uses Gson TypeToken)
-keep class com.football.livescores.matchlive.utils.** { *; }
-keepclassmembers class com.football.livescores.matchlive.utils.** { *; }

# Keep base classes
-keep class com.football.livescores.matchlive.base.** { *; }
-keepclassmembers class com.football.livescores.matchlive.base.** { *; }

# ==============================================================================
# GSON — CRITICAL FOR JSON DESERIALIZATION IN RELEASE
# ==============================================================================

# Gson uses generic type information stored in a class file when working with fields.
# R8 strips this info. These rules prevent that.
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Keep Gson internals for TypeToken and generic type resolution
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# For Gson with R8, keep fields with @SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep all classes that Gson might need to deserialize via reflection
# This is essential for data classes without @SerializedName annotations
-keepclassmembers class * {
    <init>(...);
}

# Prevent R8 from stripping generic signatures needed by Gson
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ==============================================================================
# RETROFIT & OKHTTP
# ==============================================================================

-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep Retrofit service interfaces so their generic return types aren't stripped
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }


# ==============================================================================
# FACEBOOK SDK
# ==============================================================================
-keep class com.facebook.** { *; }
-dontwarn com.facebook.infer.annotation.Nullsafe
-dontwarn com.facebook.infer.annotation.Nullsafe$Mode
-dontwarn com.facebook.infer.annotation.Nullsafe**
-dontwarn com.facebook.infer.annotation.**
-dontwarn com.facebook.common.connectionclass.**

# ==============================================================================
# ADJUST SDK
# ==============================================================================
-keep class com.adjust.sdk.** { *; }
-dontwarn com.adjust.sdk.**

# ==============================================================================
# APPSFLYER SDK
# ==============================================================================
-dontwarn com.appsflyer.**
-keep class com.appsflyer.** { *; }

# ==============================================================================
# LOTTIE
# ==============================================================================
-keep class com.airbnb.lottie.** { *; }

# ==============================================================================
# BUBBLE TAB BAR (CUSTOM VIEW AND COMPONENT SETTERS)
# ==============================================================================
-keep class io.ak1.bubbletabbar.** { *; }
-dontwarn io.ak1.bubbletabbar.**

# ==============================================================================
# GOOGLE PLAY BILLING
# ==============================================================================
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.api.**

# ==============================================================================
# HILT & GLIDE & COROUTINES
# ==============================================================================
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ==============================================================================
# FIREBASE
# ==============================================================================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ==============================================================================
# KOTLIN SPECIFIC
# ==============================================================================
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep Kotlin data class component functions and copy
-keepclassmembers class * {
    public ** component*();
    public ** copy(...);
}

# ==============================================================================
# ADMOB MEDIATION ADAPTERS (Keep adapters from being obfuscated)
# ==============================================================================
-keep class com.google.ads.mediation.** { *; }

# ==============================================================================
# INMOBI SDK
# ==============================================================================
-keep class com.inmobi.** { *; }
-dontwarn com.inmobi.**
-keep public class com.google.android.gms.**
-dontwarn com.google.android.gms.**
-dontwarn com.squareup.picasso.**

# ==============================================================================
# Pangle (Bytedance) & Adapter
-keep class com.bytedance.sdk.** { *; }
-keep interface com.bytedance.sdk.** { *; }
-keep class com.bytedance.adsdk.** { *; }
-keep class com.bytedance.mobsec.** { *; }
-keep class com.bytedance.applog.** { *; }
-keep class com.apm.insight.** { *; }
-keep class com.bykv.vk.** { *; }
-keep interface com.bykv.vk.** { *; }
-keep class com.com.bytedance.overseas.sdk.** { *; }
-keep class com.iab.omid.library.bytedance2.** { *; }
-keep class com.pgl.ssdk.** { *; }
-keep class com.tiktok.** { *; }
-keep class com.bytedance.sdk.openadsdk.** { *; }
-keep class com.bykv.vk.openvk.** { *; }
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Pangle JNI & Native Security (Crucial for Bidding Token Generation)
-keep class ms.bd.o.Pgl.** { *; }
-keep class ms.bd.c.** { *; }
-keep class ms.bd.o.** { *; }
-keep class com.pgl.sys.ces.** { *; }

# Google AdMob Mediation Adapter for Pangle
-keep class com.google.ads.mediation.pangle.** { *; }

-dontwarn com.bytedance.**
-dontwarn com.bykv.**
-dontwarn com.pgl.**
-dontwarn ms.bd.**
-dontwarn com.iab.omid.**
-dontwarn com.tiktok.**
-dontwarn com.google.ads.mediation.pangle.**

# ==============================================================================
# MINTEGRAL SDK
# ==============================================================================
-keep class com.mbridge.** { *; }
-dontwarn com.mbridge.**
-keep interface com.mbridge.** { *; }
-keep class * extends com.mbridge.msdk.out.BaseExtraInterfaceForHandler { *; }
-keep class com.google.ads.mediation.mintegral.** { *; }

# ==============================================================================
# UNITY ADS SDK
# ==============================================================================
-keep class com.unity3d.ads.** { *; }
-dontwarn com.unity3d.ads.**

# ==============================================================================
# LIFTOFF / VUNGLE SDK
# ==============================================================================
-keep class com.vungle.ads.** { *; }
-dontwarn com.vungle.ads.**

# ==============================================================================
# CSC ADS LIBRARY
# ==============================================================================
-keep class com.cscmobi.libraryads.** { *; }
-keepclassmembers class com.cscmobi.libraryads.** { *; }
-dontwarn com.cscmobi.libraryads.**
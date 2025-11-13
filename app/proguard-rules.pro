# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Supabase
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.silentzonefinder_android.**$$serializer { *; }
-keepclassmembers class com.example.silentzonefinder_android.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.silentzonefinder_android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep BuildConfig
-keep class com.example.silentzonefinder_android.BuildConfig { *; }

# Keep data classes used with Supabase
-keep class com.example.silentzonefinder_android.** { *; }
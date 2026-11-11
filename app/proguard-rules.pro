# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /path/to/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools-proguard.html

# Add any custom rules here that might be needed for your specific libraries.

# Glide rules
-keep public class com.github.bumptech.glide.GeneratedAppGlideModuleImpl { *; }
-keep public class com.github.bumptech.glide.GeneratedRequestManagerFactory { *; }

# Preserve stack frame and generic metadata required by Flogger caller-finder & Gson in R8 Full Mode
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature,Annotation

# MediaPipe & Protobuf rules (needed for JNI/native methods and R8 Full Mode)
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-keep interface com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.proto.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.protobuf.** { *; }
-keepclassmembers class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Flogger does its own caller-class stack walking internally; if R8 strips
# or renames it, MediaPipe's Graph.<clinit> crashes with
# "IllegalStateException: no caller found on the stack for: ..."
# See https://github.com/google-ai-edge/mediapipe/issues/6138
-keep class com.google.common.flogger.** { *; }
-keepclassmembers class com.google.common.flogger.** { *; }

# Gson models under AGP 9 strictFullModeForKeepRules
-keep class com.google.gson.** { *; }
-keepclassmembers class com.gowtham.library.utils.** { <fields>; <init>(...); }

# Fix R8 missing class errors
-dontwarn com.google.mediapipe.proto.**
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.yalantis.ucrop.**

# Support libraries
-keep class androidx.appcompat.widget.** { *; }
-keep class com.google.android.material.** { *; }

# Keep ViewBinding classes
-keep class **.databinding.** { *; }
-keepclassmembers class **.databinding.** {
    public static ** inflate(...);
    public static ** bind(...);
}

# Android-video-trimmer
-dontwarn com.gowtham.library**
-keep class com.gowtham.library** { *; }
-keep interface com.gowtham.library** { *; }

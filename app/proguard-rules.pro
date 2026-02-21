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

# MediaPipe rules (often need some keeps for native methods)
-keep class com.google.mediapipe.** { *; }
-keep interface com.google.mediapipe.** { *; }
-keep class com.google.android.gms.tasks.** { *; }

# Fix R8 missing class errors
-dontwarn com.google.mediapipe.proto.**
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**
-dontwarn com.google.errorprone.annotations.**

# Support libraries
-keep class androidx.appcompat.widget.** { *; }
-keep class com.google.android.material.** { *; }

# Keep ViewBinding classes
-keep class **.databinding.** { *; }
-keepclassmembers class **.databinding.** {
    public static ** inflate(...);
    public static ** bind(...);
}

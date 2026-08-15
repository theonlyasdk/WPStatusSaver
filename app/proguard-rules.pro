# ProGuard & R8 Optimization Rules for WP Status Saver

# Keep Model Classes & Enums
-keep class com.asdk.tools.wpstatussaver.model.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep ViewBinding generated classes
-keep class com.asdk.tools.wpstatussaver.databinding.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-dontwarn com.bumptech.glide.**

# PhotoView
-keep class com.github.chrisbanes.photoview.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**

# Material Components
-dontwarn com.google.android.material.**

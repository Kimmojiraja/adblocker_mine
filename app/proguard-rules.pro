# AdShield Proguard / R8 Rules
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Keep DNS & Service models
-keep class com.adshield.app.model.** { *; }
-keep class com.adshield.app.service.** { *; }

# Keep WebKit JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Brave adblock-rust JNI classes and native bindings
-keep class org.mlm.adblock.** { *; }
-keepclassmembers class org.mlm.adblock.** { *; }

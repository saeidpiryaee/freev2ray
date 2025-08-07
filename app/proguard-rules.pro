# -- APP-SPECIFIC RULES --
-keep class com.pinkypromise.v2rayconfig.MyApp { *; }
-keep class com.pinkypromise.v2rayconfig.MainActivity { *; }
-keepnames class com.pinkypromise.** { *; }

# -- COMPOSE --
-keep class androidx.compose.** { *; }
-keep class androidx.activity.ComponentActivity { *; }
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keep class androidx.lifecycle.ViewModel { *; }

# -- COROUTINES --
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# -- YANDEX ADS --
-keep class com.yandex.** { *; }
-dontwarn com.yandex.**

# -- ZXING QR CODE --
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# -- JSON HANDLING --
-keep class org.json.** { *; }
-dontwarn org.json.**

# -- GENERAL RULES --
-keepclassmembers class * {
    public <init>(android.content.Context);
}

# -- OPTIONAL RELEASE-ONLY --
# Uncomment below to remove logs and Toasts in production
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
#     public static *** w(...);
#     public static *** e(...);
# }
# -assumenosideeffects class android.widget.Toast {
#     public static android.widget.Toast makeText(...);
#     public void show();
# }

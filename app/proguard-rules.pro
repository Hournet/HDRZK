# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Сохраняем классы для автообновления
-keep class com.hournet.hdrzk.helper.** { *; }

# Сохраняем основную активность
-keep class com.hournet.hdrzk.MainActivity { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.**

# JSON (для парсинга GitHub API)
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
}

# Compose
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }

# AndroidX
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Сохраняем информацию о номерах строк для отладки
-keepattributes SourceFile,LineNumberTable

# Убираем предупреждения
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler
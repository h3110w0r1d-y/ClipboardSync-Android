# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
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

-dontpreverify
-dontoptimize
-verbose

-keepattributes Exceptions

-keepclassmembers class *{
     public<init>(org.json.JSONObject);
}

-keepclassmembers enum *{
    publicstatic**[] values();
    publicstatic** valueOf(java.lang.String);
}

-adaptclassstrings

-keep class org.eclipse.paho.mqttv5.client.logging.JSR47Logger { *; }

-keep class com.h3110w0r1d.clipboardsync.Hook { *; }
-keep class com.h3110w0r1d.clipboardsync.utils.ModuleUtils { *; }

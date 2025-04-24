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

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable

-keepclassmembers class *{
     public<init>(org.json.JSONObject);
}

-keepclasseswithmembernames class * {
     native <methods>;
}

-keepclassmembers enum *{
    publicstatic**[] values();
    publicstatic** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keepclassmembers class **.R$* { *; }


-dontwarn kotlin.**
-dontnote kotlinx.serialization.SerializationKt
-dontwarn android.support.v4.**
-dontwarn android.support.v7.**

-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class android.support.v4.** { *; }
-keep class android.support.v7.** { *; }
-keep interface android.support.v4.** { *; }
-keep interface android.support.v7.app.** { *; }
-keep public class * extends android.support.v4.**
-keep public class * extends android.support.v7.**

-keep public class * extends android.app.Fragment
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

-adaptclassstrings

-keep class org.eclipse.** { *; }

-keep class com.h3110w0r1d.clipboardsync.App { *; }
-keep class com.h3110w0r1d.clipboardsync.Hook { *; }
-keep class com.h3110w0r1d.clipboardsync.HookKt { *; }

-keep class com.h3110w0r1d.clipboardsync.activity.** { *; }
-keep class com.h3110w0r1d.clipboardsync.service.** { *; }
-keep class com.h3110w0r1d.clipboardsync.entity.** { *; }

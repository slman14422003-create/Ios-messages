-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class app.ios.messages.ChatTarget { *; }
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(android.app.Application); }
-keep class app.ios.messages.SmsReceiver { *; }
-keep class app.ios.messages.MmsReceiver { *; }
-keep class app.ios.messages.HeadlessSmsSendService { *; }
-dontwarn kotlin.**

# Release builds print nothing but errors to logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

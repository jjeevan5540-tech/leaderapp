# ProGuard Rules for Firebase, Firestore, Room, and Model classes

# Keep Firestore and Firebase models
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class com.google.firebase.** { *; }
-keep class com.google.firestore.** { *; }
-keep class com.example.data.model.** { *; }

# Keep Parcelable and Serializable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialVersionUID;
    private writeObject(java.io.ObjectOutputStream);
    private readObject(java.io.ObjectInputStream);
    readResolve();
    writeReplace();
}

# Keep Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Preserve line number information for crash reports
-keepattributes SourceFile,LineNumberTable

# Strip Log.d, Log.v, Log.i in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ProGuard Rules for Firebase, Firestore, Room, Moshi, Retrofit, and Model classes

# Preserve reflection annotations, signatures, line numbers
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# Keep all Data Model classes (Entities, DTOs, Enums)
-keep class com.example.data.model.** { *; }

# Room Database Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>();
}
-keep class **.*Dao { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.PrimaryKey *;
    @androidx.room.ColumnInfo *;
}

# Moshi JSON Rules
-keep class com.squareup.moshi.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json *;
}
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.JsonQualifier *;
}
-dontwarn com.squareup.moshi.**

# Retrofit & OkHttp Rules
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.** <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Firebase & Firestore Rules
-keep class com.google.firebase.** { *; }
-keep class com.google.firestore.** { *; }

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

# Strip verbose and debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}


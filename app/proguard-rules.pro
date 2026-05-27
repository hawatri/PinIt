# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep source file names + line numbers so crash reports stay readable.
# The class names themselves are still obfuscated.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Reflection-heavy libraries need these. Without RuntimeVisibleAnnotations the
# Google API client can't find @Key, without Signature Gson can't read generic
# types like List<FormatRange>, and the Drive deserialiser ends up calling
# .getClass() on nulls.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes Exceptions

# ===== Gson — uses reflection on data classes =====
# Keep all our serialised types' fields so Gson can read/write them.
-keep class com.hawatri.pinit.data.** { *; }
-keep class com.hawatri.pinit.ui.ChecklistItemData { *; }
-keep class com.hawatri.pinit.ui.LinkNoteData { *; }
-keep class com.hawatri.pinit.ui.ContactNoteData { *; }
-keep class com.hawatri.pinit.ui.LocationNoteData { *; }
-keep class com.hawatri.pinit.ui.AppNoteItem { *; }
-keep class com.hawatri.pinit.ui.AudioNoteData { *; }
-keep class com.hawatri.pinit.ui.FormatRange { *; }
-keep class com.hawatri.pinit.backup.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Gson internals — TypeToken etc.
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * extends com.google.gson.TypeAdapterFactory
-keep class * extends com.google.gson.JsonSerializer
-keep class * extends com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ===== Room — KSP-generated impls reference @Entity fields by name =====
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
-dontwarn androidx.room.paging.**

# ===== Google API Client / Drive REST v3 =====
# Heavy reflection. EVERY class that extends GenericJson uses @Key fields walked
# at runtime. Keep their members or restore-from-drive throws NPE on getClass().
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.services.drive.model.** { *; }
-keep class * extends com.google.api.client.json.GenericJson { *; }
-keep class * extends com.google.api.client.util.GenericData { *; }
-keep class com.google.auth.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-keepclassmembers class * extends com.google.api.client.json.GenericJson {
    <fields>;
    <init>(...);
}
-dontwarn com.google.api.client.**
-dontwarn com.google.auth.**
-dontwarn org.apache.http.**
-dontwarn javax.servlet.**
-dontwarn javax.naming.**
-dontwarn javax.annotation.**
-dontwarn org.joda.time.**

# ===== Google HTTP Client (parses Drive JSON via Gson backend) =====
-keep class com.google.api.client.http.** { *; }
-keep class com.google.api.client.json.** { *; }
-keep class com.google.api.client.json.gson.** { *; }
-keep class com.google.api.client.googleapis.** { *; }
-keep class com.google.api.client.extensions.** { *; }

# ===== Google Play Services / Sign-In =====
-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ===== ML Kit barcode scanner =====
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# ===== osmdroid =====
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ===== ZXing — barcode/QR generation =====
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ===== Jsoup =====
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ===== Coil — image loading =====
-keep class coil.** { *; }
-dontwarn coil.**

# ===== Biometric =====
-keep class androidx.biometric.** { *; }

# ===== Kotlin / Compose internals =====
-keepclassmembers class * {
    @kotlinx.coroutines.* *;
}
-dontwarn kotlinx.coroutines.**

# ===== Reflection used by sh.calvin.reorderable =====
-keep class sh.calvin.reorderable.** { *; }

# ===== Retain enum entries used at runtime (Gson reads them by name) =====
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== Parcelable safety =====
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ===== Nuclear safety net for Drive restore =====
# When Drive deserialisation throws NPE on getClass() it almost always means R8
# stripped a constructor or default value initialiser on a GenericJson subclass.
# Keep EVERYTHING under the Google API namespaces — accept the ~2 MB cost.
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.client.json.** { *; }
-keep class com.google.api.client.googleapis.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.services.drive.model.** { *; }
-keep class com.google.auth.** { *; }
-keep class com.google.oauth.** { *; }
-keepclasseswithmembers class * extends com.google.api.client.json.GenericJson {
    <init>(...);
    <fields>;
    <methods>;
}
-keepclasseswithmembers class * extends com.google.api.client.util.GenericData {
    <init>(...);
    <fields>;
    <methods>;
}

# ServiceLoader providers — Google API client uses META-INF/services for
# JsonFactory and HttpTransport implementations. R8's resource shrinker can
# strip these and the loader returns null → NPE on getClass().
-keep class com.google.api.client.json.JsonFactory
-keep class com.google.api.client.json.gson.GsonFactory { *; }
-keep class com.google.api.client.http.HttpTransport
-keep class com.google.api.client.extensions.android.http.AndroidHttp { *; }
-keep class com.google.api.client.googleapis.javanet.GoogleNetHttpTransport { *; }

# Our entire package — last-resort safety net for anything that gets
# Gson-serialised. Cost: ~200 KB. Worth it for sanity.
-keep class com.hawatri.pinit.** { *; }

# Joda-Time and javax.annotation are referenced by api-client but not present
# at runtime on Android — silence the warnings.
-dontwarn org.joda.time.**
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**

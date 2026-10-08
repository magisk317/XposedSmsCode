-keep class io.github.magisk317.smscode.xposed.utils.ModuleUtils {
    int getModuleVersion();
}

# ==========================
# Xposed module entry / hooks
# Loaded reflectively by LSPosed via META-INF/xposed/java_init.list.
# Must stay alive under R8; app process code does not reference these classes.
# ==========================
-keep class io.github.magisk317.smscode.xp.** { *; }
-keep class io.github.magisk317.xposed.** { *; }
-keep class io.github.magisk317.smscode.xposed.** { *; }

# Safety net for Hooker implementations outside the packages above.
-keepclassmembers class * implements io.github.libxposed.api.XposedInterface$Hooker {
    <methods>;
}

# LSPosed instantiates the entry reflectively; keep both constructor forms.
-keepclassmembers class * extends io.github.libxposed.api.XposedModule {
    <init>(...);
}

# LibXposed API is provided at runtime by LSPosed framework (compileOnly dependency).
-dontwarn io.github.libxposed.api.**

# ==========================
# jsoup proguard start
-keeppackagenames org.jsoup.nodes
# jsoup proguard end
# ==========================


# ==========================
# okhttp3 start
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# okhttp3 end
# ==========================


# ==========================
# okio start
-dontwarn okio.**
# okio end
# ==========================

# ==========================
# Kotlin Serialization start
-keepattributes *Annotation*
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$Companion$* {
    ** INSTANCE;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-dontwarn kotlinx.serialization.**
# Kotlin Serialization end
# ==========================

# ==========================
# Room start
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class io.github.magisk317.smscode.data.db.AppDatabase_Impl {
    public <init>();
}
# Room end
# ==========================

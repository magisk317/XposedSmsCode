# Release-only R8 rules.
#
# Public releases own no application log channel. Internal CI releases skip
# this file in build-logic, while debug builds never load it.

-assumenosideeffects class io.github.magisk317.xposed.logging.XLog {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
    public static void log(...);
    public static void structured(...);
}

-assumenosideeffects class io.github.magisk317.xposed.logging.TaggedLog {
    public void v(...);
    public void d(...);
    public void i(...);
    public void w(...);
    public void e(...);
}

-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
    public static void wtf(...);
    public static void log(...);
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}

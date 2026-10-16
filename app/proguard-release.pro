# Release-only R8 rules.
#
# Strip verbose/debug logging call sites in release builds while keeping
# info/warn/error so the e/w ring buffer stays exportable. Debug builds,
# including -PminifyDebug, never load this file, so local diagnosis keeps
# full verbosity.

-assumenosideeffects class io.github.magisk317.xposed.logging.XLog {
    public static void v(...);
    public static void d(...);
}

-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
}

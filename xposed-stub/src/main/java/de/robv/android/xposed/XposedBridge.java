package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.Collections;
import java.util.Set;

public final class XposedBridge {

    public static int XPOSED_BRIDGE_VERSION = 0;

    private XposedBridge() {
    }

    public static void log(String text) {
        // no-op in compile-time stub
    }

    public static void log(Throwable throwable) {
        // no-op in compile-time stub
    }

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        return callback.new Unhook(hookMethod);
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(
        Class<?> hookClass,
        String methodName,
        XC_MethodHook callback
    ) {
        return Collections.emptySet();
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        return Collections.emptySet();
    }
}

package de.robv.android.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class XposedHelpers {

    private XposedHelpers() {
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
        if (classLoader == null) {
            return Class.forName(className);
        }
        return Class.forName(className, false, classLoader);
    }

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try {
            return findClass(className, classLoader);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
        String className,
        ClassLoader classLoader,
        String methodName,
        Object... parameterTypesAndCallback
    ) throws ClassNotFoundException {
        Class<?> clazz = findClass(className, classLoader);
        return findAndHookMethod(clazz, methodName, parameterTypesAndCallback);
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
        Class<?> clazz,
        String methodName,
        Object... parameterTypesAndCallback
    ) {
        if (parameterTypesAndCallback == null || parameterTypesAndCallback.length == 0) {
            throw new IllegalArgumentException("no callback defined");
        }
        Object callbackObj = parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        if (!(callbackObj instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("last argument must be XC_MethodHook");
        }

        Object[] parameterTypes = new Object[parameterTypesAndCallback.length - 1];
        System.arraycopy(parameterTypesAndCallback, 0, parameterTypes, 0, parameterTypes.length);
        Method method = findMethodBestMatch(clazz, methodName, parameterTypes);
        return XposedBridge.hookMethod(method, (XC_MethodHook) callbackObj);
    }

    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Object... parameterTypes) {
        Method[] methods = clazz.getDeclaredMethods();
        Class<?>[] expected = toParameterClasses(parameterTypes);

        for (Method method : methods) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] actual = method.getParameterTypes();
            if (actual.length != expected.length) {
                continue;
            }
            if (isAssignable(actual, expected)) {
                method.setAccessible(true);
                return method;
            }
        }

        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            return findMethodBestMatch(superClass, methodName, parameterTypes);
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + methodName);
    }

    public static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) {
        Class<?>[] parameterClasses = toParameterClasses(parameterTypes);
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterClasses);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(clazz.getName() + "#" + methodName);
        }
    }

    public static Method findMethodExactIfExists(Class<?> clazz, String methodName, Object... parameterTypes) {
        try {
            return findMethodExact(clazz, methodName, parameterTypes);
        } catch (NoSuchMethodError ignored) {
            return null;
        }
    }

    public static Method[] findMethodsByExactParameters(Class<?> clazz, Class<?> returnType, Class<?>... parameterTypes) {
        List<Method> matched = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (returnType != null && !method.getReturnType().equals(returnType)) {
                continue;
            }
            Class<?>[] actualParams = method.getParameterTypes();
            if (actualParams.length != parameterTypes.length) {
                continue;
            }
            boolean exact = true;
            for (int i = 0; i < actualParams.length; i++) {
                if (!actualParams[i].equals(parameterTypes[i])) {
                    exact = false;
                    break;
                }
            }
            if (exact) {
                method.setAccessible(true);
                matched.add(method);
            }
        }
        return matched.toArray(new Method[0]);
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        if (obj == null) {
            throw new NullPointerException("obj == null");
        }
        Method method = findMethodBestMatch(obj.getClass(), methodName, args);
        try {
            return method.invoke(obj, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        Method method = findMethodBestMatch(clazz, methodName, args);
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getObjectField(Object obj, String fieldName) {
        if (obj == null) {
            throw new NullPointerException("obj == null");
        }
        try {
            Field field = findField(obj.getClass(), fieldName);
            return field.get(obj);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getIntField(Object obj, String fieldName) {
        if (obj == null) {
            throw new NullPointerException("obj == null");
        }
        try {
            Field field = findField(obj.getClass(), fieldName);
            return field.getInt(obj);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getStaticIntField(Class<?> clazz, String fieldName) {
        try {
            Field field = findField(clazz, fieldName);
            return field.getInt(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(clazz.getName() + "#" + fieldName);
    }

    private static Class<?>[] toParameterClasses(Object... parameterTypesOrValues) {
        if (parameterTypesOrValues == null || parameterTypesOrValues.length == 0) {
            return new Class<?>[0];
        }
        Class<?>[] classes = new Class<?>[parameterTypesOrValues.length];
        for (int i = 0; i < parameterTypesOrValues.length; i++) {
            Object item = parameterTypesOrValues[i];
            if (item instanceof Class<?>) {
                classes[i] = (Class<?>) item;
            } else if (item != null) {
                classes[i] = item.getClass();
            } else {
                classes[i] = Object.class;
            }
        }
        return classes;
    }

    private static boolean isAssignable(Class<?>[] actual, Class<?>[] expected) {
        for (int i = 0; i < actual.length; i++) {
            Class<?> expectedClass = wrapPrimitive(expected[i]);
            Class<?> actualClass = wrapPrimitive(actual[i]);
            if (!actualClass.isAssignableFrom(expectedClass) && !expectedClass.equals(Object.class)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrapPrimitive(Class<?> clazz) {
        if (!clazz.isPrimitive()) {
            return clazz;
        }
        if (clazz == int.class) return Integer.class;
        if (clazz == long.class) return Long.class;
        if (clazz == boolean.class) return Boolean.class;
        if (clazz == byte.class) return Byte.class;
        if (clazz == short.class) return Short.class;
        if (clazz == float.class) return Float.class;
        if (clazz == double.class) return Double.class;
        if (clazz == char.class) return Character.class;
        return clazz;
    }
}

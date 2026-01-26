package com.tianma.xsmscode.common.utils

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Utils for reflection
 */
object ReflectionUtils {

    @JvmStatic
    fun getClass(classLoader: ClassLoader, name: String): Class<*> {
        return try {
            Class.forName(name, true, classLoader)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun getDeclaredField(cls: Class<*>, fieldName: String): Field {
        val field = try {
            cls.getDeclaredField(fieldName)
        } catch (e: NoSuchFieldException) {
            throw RuntimeException(e)
        }
        field.isAccessible = true
        return field
    }

    @JvmStatic
    fun getField(cls: Class<*>, fieldName: String): Field {
        val field = try {
            cls.getField(fieldName)
        } catch (e: NoSuchFieldException) {
            throw RuntimeException(e)
        }
        field.isAccessible = true
        return field
    }

    @JvmStatic
    fun getFieldValue(field: Field, `object`: Any?): Any? {
        return try {
            field.get(`object`)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun setFieldValue(field: Field, `object`: Any?, value: Any?) {
        try {
            field.set(`object`, value)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun getDeclaredMethod(cls: Class<*>, methodName: String, vararg paramTypes: Class<*>): Method {
        val method = try {
            cls.getDeclaredMethod(methodName, *paramTypes)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException(e)
        }
        method.isAccessible = true
        return method
    }

    @JvmStatic
    fun getMethod(cls: Class<*>, methodName: String, vararg paramTypes: Class<*>): Method {
        val method = try {
            cls.getMethod(methodName, *paramTypes)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException(e)
        }
        method.isAccessible = true
        return method
    }

    @JvmStatic
    fun invoke(method: Method, thisObject: Any?, vararg params: Any?): Any? {
        return try {
            method.invoke(thisObject, *params)
        } catch (e: InvocationTargetException) {
            val cause = e.cause
            if (cause is RuntimeException) {
                throw cause
            } else {
                throw RuntimeException(e)
            }
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }
}

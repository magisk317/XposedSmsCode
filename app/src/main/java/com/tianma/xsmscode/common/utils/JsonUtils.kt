package com.tianma.xsmscode.common.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.Reader
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Gson utils
 */
object JsonUtils {

    private fun createGson(excludeExposeAnnotation: Boolean): Gson {
        return if (excludeExposeAnnotation) {
            GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
        } else {
            Gson()
        }
    }

    @JvmStatic
    fun toJson(obj: Any?, excludeExposeAnnotation: Boolean): String {
        return createGson(excludeExposeAnnotation).toJson(obj)
    }

    @JvmStatic
    fun toJson(obj: Any?, writer: Appendable, excludeExposeAnnotation: Boolean) {
        createGson(excludeExposeAnnotation).toJson(obj, writer)
    }

    @JvmStatic
    fun <T> entityFromJson(json: String?, typeClass: Class<T>, excludeExposeAnnotation: Boolean): T {
        return createGson(excludeExposeAnnotation).fromJson(json, typeClass)
    }

    @JvmStatic
    fun <T> entityFromJson(json: Reader?, typeClass: Class<T>, excludeExposeAnnotation: Boolean): T {
        return createGson(excludeExposeAnnotation).fromJson(json, typeClass)
    }

    @JvmStatic
    fun <T> listFromJson(json: String?, typeClass: Class<T>, excludeExposeAnnotation: Boolean): List<T> {
        return createGson(excludeExposeAnnotation).fromJson(json, ListOfJson<T>(typeClass))
    }

    @JvmStatic
    fun <T> listFromJson(json: Reader?, typeClass: Class<T>, excludeExposeAnnotation: Boolean): List<T> {
        return createGson(excludeExposeAnnotation).fromJson(json, ListOfJson<T>(typeClass))
    }

    private class ListOfJson<T>(private val wrapped: Class<*>) : ParameterizedType {
        override fun getActualTypeArguments(): Array<Type> {
            return arrayOf(wrapped)
        }

        override fun getRawType(): Type {
            return List::class.java
        }

        override fun getOwnerType(): Type? {
            return null
        }
    }
}

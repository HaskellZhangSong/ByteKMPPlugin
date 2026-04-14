/*
 * Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class)

package com.bytedance.kmp.ohos_ffi.types

import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import com.bytedance.kmp.ohos_ffi.napi.checkArkTsPendingException
import com.bytedance.kmp.ohos_ffi.napi.namedProperty
import com.bytedance.kmp.ohos_ffi.napi.ref.createRef
import com.bytedance.kmp.ohos_ffi.napi.ref.deleteRef
import com.bytedance.kmp.ohos_ffi.napi.ref.getRefValue
import com.bytedance.kmp.ohos_ffi.napi.setNamedProperty
import com.bytedance.kmp.ohos_ffi.napi.types.asArray
import com.bytedance.kmp.ohos_ffi.napi.types.asBoolean
import com.bytedance.kmp.ohos_ffi.napi.types.asDouble
import com.bytedance.kmp.ohos_ffi.napi.types.asInt
import com.bytedance.kmp.ohos_ffi.napi.types.asLong
import com.bytedance.kmp.ohos_ffi.napi.types.asString
import com.bytedance.kmp.ohos_ffi.napi.types.asUInt
import com.bytedance.kmp.ohos_ffi.napi.types.createBoolean
import com.bytedance.kmp.ohos_ffi.napi.types.createDouble
import com.bytedance.kmp.ohos_ffi.napi.types.createInt
import com.bytedance.kmp.ohos_ffi.napi.types.createLong
import com.bytedance.kmp.ohos_ffi.napi.types.createString
import com.bytedance.kmp.ohos_ffi.napi.types.createUInt
import com.bytedance.kmp.ohos_ffi.napi.types.getUndefined
import com.bytedance.kmp.ohos_ffi.napi.types.isUndefined
import com.bytedance.kmp.ohos_ffi.opt.*
import com.bytedance.kmp.ohos_ffi.transform.ArkTsExportCustomTransformer
import com.bytedance.kmp.ohos_ffi.types.converter.*
import com.bytedance.kmp.ohos_ffi.types.transformer.ArrayTypeTransformer
import com.bytedance.kmp.ohos_ffi.types.transformer.BooleanTypeTransformer
import com.bytedance.kmp.ohos_ffi.types.transformer.DoubleTypeTransformer
import com.bytedance.kmp.ohos_ffi.types.transformer.IntTypeTransformer
import com.bytedance.kmp.ohos_ffi.types.transformer.ListTypeTransformer
import com.bytedance.kmp.ohos_ffi.types.transformer.LongTypeTransformer
import com.bytedance.kmp.ohos_ffi.types.transformer.MapTypeTransformer
import com.bytedance.kmp.ohos_ffi.types.transformer.SendableArrayTypeTransformer
import kotlinx.cinterop.*
import platform.ohos.napi.*
import kotlin.native.internal.createCleaner
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * desc: 线程安全的ArkTs对象
 */

private data class JSValueRef(
    var ref: napi_ref?,
    val tid: Int,
    val finalize: (() -> Unit)?
)

private const val JSValueCleanerSize = 2_000
private var jsValueCleaner = ArrayList<napi_ref>(JSValueCleanerSize)

/**
 *  JSValue ：JavaScript 一个值
 *  封装 napi_value，可能是对象，方法，或任意值
 */
open class ArkObjectSafeReference(val env: napi_env, origin: napi_value, val tid: Int, val finalize: (() -> Unit)?) {

    constructor(origin: napi_value) : this(OhosFFIManager.tlsEnv, origin, get_tid(), null)
    constructor(origin: napi_value, tid: Int) : this(OhosFFIManager.tlsEnv, origin, tid, null)
    constructor(env: napi_env, origin: napi_value) : this(env, origin, get_tid(), null)
    constructor(env: napi_env, origin: napi_value, tid: Int) : this(env, origin, tid, null)

    private val napiRef = origin.createRef()
    val isSendable = napi_is_sendable_opt(env, origin)
//    private val jsValueRef = JSValueRef(ref, tid, finalize)

    private val clean = createCleaner(this) {
        // Kotlin gc need delete Reference.
        if (it.tid == mainTid) {
            // 仅在主线程进行分批清理操作
            jsValueCleaner.add(it.napiRef)
            if (jsValueCleaner.size >= JSValueCleanerSize) {
                val jsValueCleanerRef = jsValueCleaner
                jsValueCleaner = ArrayList<napi_ref>(JSValueCleanerSize)
                runOnMainThread({
                    jsValueCleanerRef.forEach {
                        it.deleteRef()
                    }
                    jsValueCleanerRef.clear()
                }, Random.nextLong(2000, 10_000))
            }
        } else {
            // 在非主线程切换至该线程，进行清理操作
            tsfnRegister.callAsyncSafe(it.tid) {
                it.napiRef.deleteRef()
                it.finalize?.invoke()
            }
        }

    }

    /**
     * 被包装的 napi_value 指针
     */
    val handle: napi_value
        get() {
            if (get_tid() != tid) {
                throw RuntimeException("JSValue#handle cannot run in multi-thread! thread:${tid} currentThread:${get_tid()}")
            }
            return  napiRef.getRefValue()
        }

    companion object {
        /**
         * 获取 global 对象
         * @param tid JavaScript 线程 id，默认值为当前线程
         * @return 当前线程的 global 对象
         */
        fun global(tid: Int = get_tid()): ArkObjectSafeReference? {
            return tsfnRegister.callSyncSafe(tid) {
                val g = getGlobal(OhosFFIManager.tlsEnv)
                if (g == null || g.isUndefined()) {
                    return@callSyncSafe null
                }
                return@callSyncSafe ArkObjectSafeReference(g)
            }!!
        }
    }

    operator fun get(index: Int): ArkObjectSafeReference? {
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe if (!checkArrayIndex(index)) {
                null
            } else {
                val element = napi_get_element_opt(OhosFFIManager.tlsEnv, handle, index) as napi_value
                if (element == null || element.isUndefined()) {
                    null
                } else {
                    ArkObjectSafeReference(element)
                }
            }
        }
    }

    operator fun set(index: Int, value: ArkObjectSafeReference): Boolean {
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe if (!checkArrayIndex(index)) {
                false
            } else {
                napi_set_element(OhosFFIManager.tlsEnv, handle, index.toUInt(), value.handle)
                true
            }
        }!!
    }

    private fun checkArrayIndex(index: Int): Boolean {
        return tsfnRegister.callSyncSafe(tid) {
            if (!isArrayType()) {
                return@callSyncSafe false
            }
            val length = napi_get_array_length_opt(OhosFFIManager.tlsEnv, handle)
            return@callSyncSafe index < length
        }!!
    }

    operator fun get(key: String): ArkObjectSafeReference? {
        return tsfnRegister.callSyncSafe(tid) {
            if (!isObject() || key.isEmpty()) {
                return@callSyncSafe null
            }
            val prop = getPropertyValue(OhosFFIManager.tlsEnv, handle, key)
            if (prop == null || prop.isUndefined()) {
                return@callSyncSafe null
            }
            ArkObjectSafeReference(prop)
        }
    }

    operator fun set(key: String, value: ArkObjectSafeReference): Boolean {
        return tsfnRegister.callSyncSafe(tid) {
            if (!isObject() || key.isEmpty()) {
                return@callSyncSafe false
            }
            setPropertyValue(OhosFFIManager.tlsEnv, handle, key, value.handle)
            true
        }!!
    }

    inline fun<reified T: Any> getProperty(key: String, crossinline transformer: (napi_value) -> T): T? {
        return tsfnRegister.callSyncSafe(tid) {
            val prop = getPropertyValue(env, handle, key)
            if (prop == null || prop.isUndefined()) {
                return@callSyncSafe null
            }
            transformer(prop)
        }
    }

    inline fun<reified T: Any> setProperty(key: String, value: T?, crossinline transformer: (T) -> napi_value): Boolean? {
        return tsfnRegister.callSyncSafe(tid) {
            val napiValue = if (value == null) {
                getUndefined()
            } else {
                transformer(value)
            }
            handle.setNamedProperty(key, napiValue)
            true
        }
    }

    fun getBoolean(key: String): Boolean? {
        return getProperty(key) { it.asBoolean() }
    }

    fun setBoolean(key: String, value: Boolean?): Boolean? {
        return setProperty(key, value) { createBoolean(it) }
    }

    fun getInt(key: String): Int? {
        return getProperty(key) { it.asInt() }
    }

    fun setInt(key: String, value : Int?): Boolean? {
        return setProperty(key, value) { createInt(it) }
    }

    fun getUInt(key: String): UInt? {
        return getProperty(key) { it.asUInt() }
    }

    fun setUInt(key: String, value : UInt?): Boolean? {
        return setProperty(key, value) { createUInt(it) }
    }

    fun getLong(key: String): Long? {
        return getProperty(key) { it.asLong() }
    }

    fun setLong(key: String, value: Long?): Boolean? {
        return setProperty(key, value) { createLong(it) }
    }

    fun getFloat(key: String): Float? {
        return getProperty(key) { it.asDouble().toFloat() }
    }

    fun setFloat(key: String, value: Float?): Boolean? {
        return setProperty(key, value) { createDouble(it.toDouble()) }
    }
    fun getDouble(key: String): Double? {
        return getProperty(key) { it.asDouble() }
    }

    fun setDouble(key: String, value: Double?): Boolean? {
        return setProperty(key, value) { createDouble(it) }
    }

    fun getString(key: String): String? {
        return getProperty(key) { it.asString() }
    }

    fun setString(key: String, value: String?): Boolean? {
        return setProperty(key, value) { createString(it) }
    }
    fun getBooleanArray(key: String): BooleanArray? {
        return getArray(key, BooleanTypeTransformer)?.toBooleanArray()
    }

    fun setBooleanArray(key: String, value: BooleanArray?): Boolean? {
        return setArray(key, value?.toList(), BooleanTypeTransformer)
    }

    fun getIntArray(key: String): IntArray? {
        return getArray(key, IntTypeTransformer)?.toIntArray()
    }

    fun setIntArray(key: String, value: IntArray?): Boolean? {
        return setArray(key, value?.toList(), IntTypeTransformer)
    }

    fun getLongArray(key: String): LongArray? {
        return getArray(key, LongTypeTransformer)?.toLongArray()
    }

    fun setLongArray(key: String, value: LongArray?): Boolean? {
        return setArray(key, value?.toList(), LongTypeTransformer)
    }

    fun getDoubleArray(key: String): DoubleArray? {
        return getArray(key, DoubleTypeTransformer)?.toDoubleArray()
    }

    fun setDoubleArray(key: String, value: DoubleArray?): Boolean? {
        return setArray(key, value?.toList(), DoubleTypeTransformer)
    }

    fun<T> getArray(key: String, itemTransformer: ArkTsExportCustomTransformer<T>): List<T>? {
        return getProperty(key) {
            if (isSendable) {
                SendableArrayTypeTransformer(itemTransformer).fromJsObject(it)
            } else {
                ArrayTypeTransformer(itemTransformer).fromJsObject(it)
            }
        }
    }

    fun<T> setArray(key: String, value: List<T>?, itemTransformer: ArkTsExportCustomTransformer<T>): Boolean? {
        return setProperty(key, value) {
            if (isSendable) {
                SendableArrayTypeTransformer(itemTransformer).toJsObject(it)
            } else {
                ArrayTypeTransformer(itemTransformer).toJsObject(it)
            }
        }
    }

    fun<T> getList(key: String, itemTransformer: ArkTsExportCustomTransformer<T>): List<T>? {
        return getProperty(key) {
            if (isSendable) {
                SendableArrayTypeTransformer(itemTransformer).fromJsObject(it)
            } else {
                ListTypeTransformer(itemTransformer).fromJsObject(it)
            }
        }
    }

    fun<T> setList(key: String, value: List<T>?, itemTransformer: ArkTsExportCustomTransformer<T>): Boolean? {
        return setProperty(key, value) {
            if (isSendable) {
                SendableArrayTypeTransformer(itemTransformer).toJsObject(it)
            } else {
                ListTypeTransformer(itemTransformer).toJsObject(it)
            }
        }
    }

    /**
     * 调用 JavaScript 对象的方法
     * @param name 方法米给你做
     * @param params 传入方法调用的参数包
     * @return 方法调用返回值
     */
    inline fun <reified T : Any> callMethod(name: String, vararg params: Any?): T? {
        return trace("JSValue callMethod $name") {
            var exception: Exception? = null
            val result = tsfnRegister.callSyncSafe<T>(tid) {
                try {
                    // TODO: 多次的call sync safe
                    val func = this[name]
                    if (func == null) {
                        println("name = $name is null.")
                        return@callSyncSafe null
                    }
//                    if (isDebug) {
                        if (func.isUndefined()) {
                            println("name = $name is undefined. Did forget to configure obfuscation?")
                            return@callSyncSafe null
                        }
                        if (!func.isFunction()) {
                            println("name = $name not a Function.")
                            return@callSyncSafe null
                        }
//                    }
                    return@callSyncSafe safeCaseNumberType(
                        invokeDirect(
                            params,
                            T::class,
                            func,
                            this
                        ), T::class
                    ) as T?
                } catch (e: Exception) {
                    exception = e
                }
                return@callSyncSafe null
            }
            // 跨线程调用，重新抛出异常
            if (exception != null) {
                println("JSValue callMethod throw Exception. ${exception!!.message}")
                throw exception!!
            }
            result
        }
    }

    /**
     * 调用 JSValue 方法
     * @param params 参数包
     * @return 方法调用返回值
     */
    inline fun <reified T : Any> invoke(vararg params: Any?): T? {
        return trace("JSValue:invoke") {
            tsfnRegister.callSyncSafe(tid) {
                val jsFunction = JSFunction(OhosFFIManager.tlsEnv, "", handle)
                return@callSyncSafe jsFunction.invoke(*params)
            }
        }
    }

    private inline fun <reified T : Any> convertType(clazz: KClass<T>): T? {
        if (isNotAvailable()) return null
        // TODO: 低效率的类型转换
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe jsValueToKTValue(OhosFFIManager.tlsEnv, handle, clazz) as T
        }
    }

    /**
     * JSValue 转换 Int 类型
     */
    fun toInt(): Int {
        return convertType(Int::class) ?: 0
    }

    /**
     * JSValue 转换 Double 类型
     */
    fun toDouble(): Double = convertType(Double::class) ?: 0.0

    /**
     * JSValue 转换 Long 类型
     */
    fun toLong(): Long = convertType(Long::class) ?: 0L

    /**
     * JSValue 转换 字符串类型
     */
    fun toKString(): String? {
        return convertType(String::class)
    }

    /**
     * JSValue 转 Boolean
     */
    fun toBoolean(): Boolean = convertType(Boolean::class) ?: false

    /**
     * JSValue（JS 对象）转 Map<String, Any?>
     */
    @Suppress("UNCHECKED_CAST")
    fun toMap(): Map<String, Any?> = convertType(Map::class) as Map<String, Any?>

    /**
     * JSValue 转数组，仅支持同类型数组
     */
    inline fun <reified T : Any> toArray(): Array<T> {
        return toList<T>().toTypedArray()
    }

    /**
     * JSValue 转 List，仅支持同类型
     */
    inline fun <reified T : Any> toList(): List<T> {
        if (isNotAvailable()) return emptyList()
        return tsfnRegister.callSyncSafe(tid) {
            val result = mutableListOf<T>()
            val length = napi_get_array_length_opt(OhosFFIManager.tlsEnv, handle)
            for (index in 0 until length) {
                result.add(jsValueToKTValue(OhosFFIManager.tlsEnv, this[index]?.handle, T::class) as T)
            }
            return@callSyncSafe result
        }!!
    }

    /**
     * 获取 JSValue 包装的 napi_value 类型
     */
    private fun getType(): napi_valuetype {
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe typeOf(OhosFFIManager.tlsEnv, handle)
        }!!
    }

    /**
     * 判断是否为 null 或 undefined
     * JavaScript 中 undefined 和 null 都映射到 Kotlin 中的 null
     */
    fun isNotAvailable(): Boolean = isUndefined() || isNull()

    /**
     * 是否为 undefined
     */
    fun isUndefined(): Boolean = getType() == napi_valuetype.napi_undefined

    /**
     * 是否为 Boolean 类型
     */
    fun isBoolean(): Boolean = getType() == napi_valuetype.napi_boolean

    /**
     * 是否为 Function 类型
     */
    fun isFunction(): Boolean = getType() == napi_valuetype.napi_function

    /**
     * 是否为 Object 类型
     */
    fun isObject(): Boolean = getType() == napi_valuetype.napi_object

    /**
     * 是否字符串类型
     */
    fun isString(): Boolean = getType() == napi_valuetype.napi_string

    /**
     * 是否为 null
     */
    fun isNull(): Boolean = getType() == napi_valuetype.napi_null

    /**
     * 是否为数组类型
     */
    fun isArrayType(): Boolean {
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe napi_is_array_opt(OhosFFIManager.tlsEnv, handle)
        }!!
    }
}

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

@file:OptIn(ExperimentalForeignApi::class)
package com.bytedance.kmp.ohos_ffi.types.converter


import platform.ohos.napi.*
import com.bytedance.kmp.ohos_ffi.opt.*
import kotlinx.cinterop.*
import kotlin.reflect.KClass
import com.bytedance.kmp.ohos_ffi.types.ArkObjectSafeReference

/**
 * Map 类型映射未 JavaScript 的 Object 类型
 */
class MapTypeConverter : TypeConverter<Map<String, Any?>> {

    private val emptyMapKClass = emptyMap<Nothing, Nothing>()::class

    override fun convertJSValueToKotlinValue(
        env: napi_env?, value: napi_value?
    ): Map<String, Any?> {
        return if (isJSMap(env, value)) {
            convertJSMapToKotlinValue(env, value)
        } else {
            convertJSObjectToKotlinValue(env, value)
        }
    }

    private fun isJSMap(env: napi_env?, value: napi_value?): Boolean {
        return isInstanceOf(env,  ArkObjectSafeReference.global()?.get("Map")?.handle, value)
    }

    private fun convertJSMapToKotlinValue(
        env: napi_env?, value: napi_value?
    ): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val jsMap = ArkObjectSafeReference(value!!)
        val keysIterator = jsMap.callMethod<ArkObjectSafeReference>("keys")!!
        while (true) {
            val keyValuePair = keysIterator.callMethod<ArkObjectSafeReference>("next") ?: continue
            val done = keyValuePair["done"] ?: continue

            if (done.isBoolean() && done.toBoolean()) {
                break
            }
            val key = keyValuePair["value"]?.toKString() ?: continue
            val valueOfKey = jsMap.callMethod<ArkObjectSafeReference>("get", key)
            if (valueOfKey?.handle != null) {
                val converter = getFirstSupportConverter(env, valueOfKey.handle)
                val ktValue = converter.convertJSValueToKotlinValue(env, valueOfKey.handle)
                map[key] = ktValue
            } else {
                map[key] = null
            }

        }
        return map.toMap()
    }

    private fun convertJSObjectToKotlinValue(
        env: napi_env?, value: napi_value?
    ): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val names = getPropertyNames(env, value)
        val length = napi_get_array_length_opt(env, names)
        for (i in 0 until length) {
            val key = napi_get_element_opt(env, names, i) as napi_value
            val keyStr = jsValueToKTValue(env, key, String::class) as String
            val valueOfKey = getPropertyValue(env, value, keyStr)
            val converter = getFirstSupportConverter(env, valueOfKey)
            val ktValue = converter.convertJSValueToKotlinValue(env, valueOfKey)
            map[keyStr] = ktValue
        }
        return map.toMap()
    }

    override fun isSupportKType(type: KClass<out Any>): Boolean {
        // 由于 K/N 支持的反射能力有限，无法判断KClass 继承关系
        return Map::class == type || getKType() == type || type == emptyMapKClass
    }

    override fun getJSType(): napi_valuetype {
        return napi_valuetype.napi_object
    }

    override fun getKType(): KClass<out Any> = HashMap::class

    override fun convertKotlinValueToJSValue(
        env: napi_env?, value: Map<String, Any?>?
    ): napi_value? {
        if (value == null) {
            return null
        }
        val obj = createObject(env)
        value.forEach {
            val key = it.key
            val valueOfKey = it.value ?: return@forEach
            val typeConverter = getFirstSupportConverter(valueOfKey::class)
            val napiValue = ktValueToJSValue(env, valueOfKey, typeConverter.getKType())
            setPropertyValue(env, obj, key, napiValue)
        }
        return obj
    }
}
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

@file:Suppress("UNCHECKED_CAST")
@file:OptIn(ExperimentalForeignApi::class)
package com.bytedance.kmp.ohos_ffi.types.converter


import platform.ohos.napi.*
import com.bytedance.kmp.ohos_ffi.opt.*
import kotlinx.cinterop.*
import kotlin.reflect.KClass
import kotlin.reflect.*
import com.bytedance.kmp.ohos_ffi.trace
import com.bytedance.kmp.ohos_ffi.types.ArkObjectSafeReference
import platform.posix.free

val converters = arrayOf(
    // 无类型辅助推导的情况下，依赖顺序
    UnitTypeConverter(),
    BooleanTypeConverter(),
    IntTypeConverter(),
    LongTypeConverter(),
    DoubleTypeConverter(),
    StringTypeConverter(),
    ArrayBufferTypeConverter(),
    ArrayTypeConverter(),
    ListTypeConverter(),
    JSCallbackTypeConverter(),
    MapTypeConverter(),

    // 上面添加实现，JSValue 为 Any。
    JSValueTypeConverter()
)

/**
 * 将 Kotlin 类型 转为 JavaScript 类型
 * 转换规则见 “类型转换”章节
 */
fun ktValueToJSValue(env: napi_env?, value: Any?, clazz: KClass<out Any>): napi_value? {
    if (env == null || value == null) {
        return null
    }
    val typeConverter = getFirstSupportConverter(clazz)
    return typeConverter.convertKotlinValueToJSValueWithAny(env, value)
}

/**
 * 将 JavaScript 类型转 Kotlin 类型
 * 转换规则见 “类型转换”章节
 */
fun jsValueToKTValue(
    env: napi_env?, value: napi_value?, kType: KClass<out Any> = Any::class
): Any? {
    if (env == null || value == null) {
        return null
    }
    if (kType == Unit::class) {
        return Unit
    }
    val type = typeOf(env, value)
    if (type == napi_valuetype.napi_undefined || type == napi_valuetype.napi_null) {
        return null
    }
    return getFirstSupportConverter(kType).convertJSValueToKotlinValue(env, value)
}

/**
 * 获取 支持转换 type 类型的类型转换器
 * @param type Kotlin 类型
 * @return 类型转换器
 */
fun getFirstSupportConverter(type: KClass<out Any>): TypeConverter<out Any> {
    val typeConverter = converters.find {
        return@find it.isSupportKType(type)
    }
    if (typeConverter != null) {
        return typeConverter
    }
    if (type.toString().contains("$") || type.toString().contains("kotlin.Function")) {
        return getFirstSupportConverter(Function::class)
    }
    throw RuntimeException("UnSupportTypeException kotlin type = ${type.toString()} ${type.simpleName}")

}

/**
 * 获取 支持转换 napi_value 类型的类型转换器
 * @param value napi_value
 * @return 类型转换器
 */
fun getFirstSupportConverter(env: napi_env?, value: napi_value?): TypeConverter<out Any> {
    val type = typeOf(env, value)
    val typeConverter = converters.find {
        return@find it.isSupportJSType(env, type, value)
    }

    if (typeConverter == null) {
        throw RuntimeException("UnSupportTypeException js type = $type")
    } else {
        return typeConverter
    }
}

fun <T> convertJSCallbackInfoToKTParamList(
    env: napi_env?,
    callbackInfo: napi_callback_info?,
    paramsType: List<KClass<out Any>>? = null,
    offset: Int = 0
): MutableList<T?> {
    return trace("TypeConvertCenter:convertJSCallbackInfoToKTParamList") {
        val jsParamsSize = getCallbackInfoParamsSize(env, callbackInfo)
        val params = getCbInfoWithSize(env, callbackInfo, jsParamsSize) ?: error("unknown params.")

        val paramsValue = mutableListOf<T?>()
        try {

            for (index in offset until jsParamsSize) {
                val kType = if (paramsType != null) {
                    paramsType[index - offset]
                } else {
                    ArkObjectSafeReference::class
                }
                val ktValue = jsValueToKTValue(env, params[index], kType)
                paramsValue.add(ktValue as T?)
            }

        } finally {
            free(params)
        }
        paramsValue
    }

}
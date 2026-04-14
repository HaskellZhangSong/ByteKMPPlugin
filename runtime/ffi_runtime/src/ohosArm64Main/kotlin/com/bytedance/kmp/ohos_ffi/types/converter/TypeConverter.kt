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

interface TypeConverter<T> {

    /**
     * 转换 napi_value 为 Kotlin 值
     */
    fun convertJSValueToKotlinValue(env: napi_env?, value: napi_value?): T?

    @Suppress("UNCHECKED_CAST")
    fun convertKotlinValueToJSValueWithAny(env: napi_env?, value: Any?): napi_value? {
        return convertKotlinValueToJSValue(env, value as T)
    }

    /**
     * 转换 Kotlin 值 为 napi_value
     */
    fun convertKotlinValueToJSValue(env: napi_env?, value: T?): napi_value?

    /**
     * 是否为支持的 Kotlin 类型
     * 由于 Kotlin Native 无法运行时判断类继承关系，通过该方法补充支持的类型实现
     * @param type Kotlin 类型
     * @return 是为支持的类型
     */
    fun isSupportKType(type: KClass<out Any>): Boolean {
        return type == getKType()
    }

    /**
     * 默认支持的 Kotlin 类型
     */
    fun getKType(): KClass<out Any>

    /**
     * 获取支持的 JS 类型
     */
    fun getJSType(): napi_valuetype {
        return napi_valuetype.napi_null
    }

    /**
     * JS & KT 类型映射表
     * JS Type <-> KT Type
     * Object <-> HashMap<String,*>
     * Boolean <-> Boolean
     * Number <-> Double
     * Function <-> (args: Array<out Any?>) -> Any?>
     * String <-> String
     */
    fun isSupportJSType(env: napi_env?, type: napi_valuetype, value: napi_value?): Boolean {
        return getJSType() == type
    }
}
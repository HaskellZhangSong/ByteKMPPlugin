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

open class ArrayTypeConverter : TypeConverter<Array<Any?>> {
    override fun convertJSValueToKotlinValue(env: napi_env?, value: napi_value?): Array<Any?>? {
        if (typeOf(env, value) == napi_valuetype.napi_undefined) {
            return null
        }
        val length = napi_get_array_length_opt(env, value)
        val result = Array<Any?>(length) {}
        if (length == 0) {
            return result
        }
        for (index in 0 until length) {
            val element = napi_get_element_opt(env, value, index) as napi_value
            val ktElement =
                getFirstSupportConverter(env, element).convertJSValueToKotlinValue(env, element)
            result[index] = (ktElement)
        }
        return result
    }

    override fun convertKotlinValueToJSValue(env: napi_env?, value: Array<Any?>?): napi_value? {
        if (value == null) {
            return null
        }
        val array = napi_create_array_opt(env) ?: return null
        value.forEachIndexed { index, element ->
            val jsElement = if (element == null) {
                null
            } else {
                ktValueToJSValue(env, element, element::class)
            }
            napi_set_element_opt(env, array, index, jsElement)
        }
        return array as napi_value
    }

    override fun isSupportKType(type: KClass<out Any>): Boolean {
        return type == getKType()
    }

    override fun getKType(): KClass<out Any> {
        return Array::class
    }


}
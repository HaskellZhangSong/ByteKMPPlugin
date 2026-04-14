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
import platform.posix.free

class StringTypeConverter : TypeConverter<String> {
    override fun convertJSValueToKotlinValue(env: napi_env?, value: napi_value?): String? {
        if (value == null || typeOf(env, value) == napi_valuetype.napi_undefined) {
            return null
        }
        val methodNameCharArray: CPointer<ByteVar> = toKString(env, value)
            ?: return null
        val valueStr = methodNameCharArray.toKString()
        free(methodNameCharArray)
        return valueStr
    }

    override fun getKType(): KClass<out Any> = String::class

    override fun getJSType(): napi_valuetype {
        return napi_valuetype.napi_string
    }

    override fun convertKotlinValueToJSValue(env: napi_env?, value: String?): napi_value? {
        if (value == null) {
            return null
        }
        return convertStringToNapiValue(env, value)
    }
}
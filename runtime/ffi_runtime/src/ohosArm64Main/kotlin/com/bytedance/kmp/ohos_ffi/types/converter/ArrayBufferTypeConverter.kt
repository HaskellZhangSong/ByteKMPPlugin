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
import com.bytedance.kmp.ohos_ffi.types.ArrayBuffer
import kotlin.reflect.KClass

/**
 * ArrayBuffer 直接操作 napi_value 的 指针，不存在数据拷贝
 * 无法映射为 Kotlin ByteArray，是因为 ByteArray 存在数据拷贝
 */
open class ArrayBufferTypeConverter : TypeConverter<ArrayBuffer> {
    override fun convertJSValueToKotlinValue(env: napi_env?, value: napi_value?): ArrayBuffer? {
        return ArrayBuffer(value)
    }

    override fun convertKotlinValueToJSValue(env: napi_env?, value: ArrayBuffer?): napi_value? {
        if (value == null) {
            return null
        }
        return value.handle
    }

    override fun isSupportJSType(env: napi_env?, type: napi_valuetype, value: napi_value?): Boolean {
        return isArrayBuffer(env, value) || isTypedArray(env, value)
    }

    override fun isSupportKType(type: KClass<out Any>): Boolean {
        return type == getKType()
    }

    override fun getKType(): KClass<out Any> {
        return ArrayBuffer::class
    }
}
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

package com.bytedance.kmp.ohos_ffi.napi.types

import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import com.bytedance.kmp.ohos_ffi.opt.napi_create_uint32_opt
import com.bytedance.kmp.ohos_ffi.opt.napi_get_value_uint32_opt
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.ohos.arkui.napi_create_uint32
import platform.ohos.napi.napi_create_bigint_int64
import platform.ohos.napi.napi_get_value_bigint_int64
import platform.ohos.napi.napi_get_value_uint32
import platform.ohos.napi.napi_value
import platform.ohos.napi.napi_valueVar

fun createDouble(value: Double): napi_value {
    return interpretCPointer(napi_create_kotlin_double(OhosFFIManager.tlsEnv.rawValue, value))!!
}

fun createInt(value: Int): napi_value {
    return interpretCPointer(napi_create_kotlin_int32(OhosFFIManager.tlsEnv.rawValue, value))!!
}

fun createUInt(value: UInt): napi_value {
    return napi_create_uint32_opt(OhosFFIManager.tlsEnv, value) as napi_value
}

fun createLong(value: Long): napi_value {
    return interpretCPointer(napi_create_kotlin_int64(OhosFFIManager.tlsEnv.rawValue, value))!!
}

fun createBigInt(value: Long): napi_value {
    val result = nativeHeap.alloc<napi_valueVar>()
    napi_create_bigint_int64(OhosFFIManager.tlsEnv, value, result.ptr)
    return result.value!!
}

fun createBoolean(value: Boolean): napi_value {
    return interpretCPointer(napi_create_kotlin_bool(OhosFFIManager.tlsEnv.rawValue, value))!!
}

fun napi_valueVar.asInt(): Int {
    return value!!.asInt()
}

fun napi_value.asInt(): Int {
    return napi_get_value_kotlin_int32(OhosFFIManager.tlsEnv.rawValue, this.rawValue)
}

fun napi_valueVar.asUInt(): UInt {
    return value!!.asUInt()
}

fun napi_value.asUInt(): UInt {
    return napi_get_value_uint32_opt(OhosFFIManager.tlsEnv, this)
}

fun napi_valueVar.asLong(): Long {
    return value!!.asLong()
}

fun napi_value.asLong(): Long {
    return napi_get_value_kotlin_int64(OhosFFIManager.tlsEnv.rawValue, this.rawValue)
}

fun napi_valueVar.asBigInt(): Long {
    return value!!.asBigInt()
}

fun napi_value.asBigInt(): Long {
    val result = nativeHeap.alloc<LongVar>()
    val lossless = nativeHeap.alloc<BooleanVar>()
    lossless.value = false
    napi_get_value_bigint_int64(OhosFFIManager.tlsEnv, this, result.ptr, lossless.ptr)
    return result.value
}

fun napi_valueVar.asBoolean(): Boolean {
    return value!!.asBoolean()
}

fun napi_value.asBoolean(): Boolean {
    return napi_get_value_kotlin_bool(OhosFFIManager.tlsEnv.rawValue, this.rawValue)
}

fun napi_valueVar.asDouble(): Double {
    return value!!.asDouble()
}

fun napi_value.asDouble(): Double {
    return napi_get_value_kotlin_double(OhosFFIManager.tlsEnv.rawValue, this.rawValue)
}
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
package com.bytedance.kmp.ohos_ffi.types

import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import com.bytedance.kmp.ohos_ffi.napi.types.asString
import com.bytedance.kmp.ohos_ffi.napi.types.createString
import com.bytedance.kmp.ohos_ffi.opt.*
import kotlinx.cinterop.*
import platform.ohos.napi.*
import kotlin.native.internal.createCleaner
import kotlin.random.Random
import kotlin.reflect.KClass
import com.bytedance.kmp.ohos_ffi.types.converter.*
import platform.posix.memcpy
import platform.posix.uint8_tVar


class ArrayBuffer() {
    private var type = napi_typedarray_type.napi_uint8_array
    private var length = 0L
    private var jsValue: napi_value? = null

    // JS 指向的内存区域
    private var data: CPointer<uint8_tVar>? = null
    private var dataInKN: UByteArray? = null

    var handle: napi_value? = null
        get() = getOrCreateHandle()

    /**
     * 以 napi_value 构造需在 JS 线程中
     */
    constructor(handle: napi_value?) : this() {
        this.jsValue = handle
        initData()
    }

    constructor(
        data: CPointer<uint8_tVar>,
        length: Long,
        type: napi_typedarray_type = napi_typedarray_type.napi_uint8_array
    ) : this() {
        this.dataInKN = data.toByteArray(length.toInt()).asUByteArray()
        this.type = type
        this.length = length
    }

    constructor(
        data: UByteArray
    ) : this() {
        this.dataInKN = data
        this.type = napi_typedarray_type.napi_uint8_array
        this.length = data.size.toLong()
    }

    constructor(
        data: ByteArray,
    ) : this(data.toUByteArray())

    private fun getOrCreateHandle(): napi_value? {
        if (jsValue != null) {
            return jsValue
        }
        if (dataInKN == null) {
            return null
        }
        memScoped {
            val ptr = dataInKN!!.toCValues().ptr
            jsValue = if (type != napi_typedarray_type.napi_uint8_array) {
                createTypedArray(OhosFFIManager.tlsEnv, ptr, length, type)
            } else {
                createArrayBuffer(OhosFFIManager.tlsEnv, ptr, length)
            }
        }

        initData()
        return jsValue
    }

    private fun initData() {
        if (isArrayBuffer(OhosFFIManager.tlsEnv, jsValue)) {
            length = getArrayBufferLength(OhosFFIManager.tlsEnv, jsValue).toLong()
            type = napi_typedarray_type.napi_uint8_array
            data = getArrayBufferValue(OhosFFIManager.tlsEnv, jsValue)
        } else {
            type = getTypeArrayType(OhosFFIManager.tlsEnv, jsValue)
            length = getTypeArrayLength(OhosFFIManager.tlsEnv, jsValue).toLong()
            data = getTypeArrayValue(OhosFFIManager.tlsEnv, jsValue)
        }
    }

    /**
     * 获取 JS ArrayBuffer 指向的数据指针
     */
    fun <T : CPointed> getData(): CPointer<T>? {
        return data?.reinterpret()
    }

    /**
     * 获取 JS ArrayBuffer 指向的数据指针
     */
    fun getByteArray(): ByteArray {
        return trace("ArrayBuffer:getByteArray") {
            data?.toByteArray(getCount().toInt()) ?: ByteArray(getCount().toInt())
        }
    }

    fun getCount(): Long {
        return length / getTypedArrayItemSize(type)
    }
}

fun CPointer<uint8_tVar>.toByteArray(count: Int): ByteArray {
    val result = ByteArray(count)
    if (result.isEmpty()) return result
    result.usePinned {
        memcpy(it.addressOf(0), this, count.toULong())
    }
    return result
}
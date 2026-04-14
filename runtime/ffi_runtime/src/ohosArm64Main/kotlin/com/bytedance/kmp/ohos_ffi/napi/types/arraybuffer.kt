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
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.ohos.napi.napi_create_arraybuffer
import platform.ohos.napi.napi_get_arraybuffer_info
import platform.ohos.napi.napi_is_arraybuffer
import platform.ohos.napi.napi_ok
import platform.ohos.napi.napi_value
import platform.ohos.napi.napi_valueVar
import platform.posix.memcpy

fun createArrayBuffer(value: ByteArray): napi_value {
    // char**
    val byteArray = nativeHeap.allocArray<CPointerVar<ByteVar>>(1)

    val result = nativeHeap.alloc<napi_valueVar>()
    napi_create_arraybuffer(OhosFFIManager.tlsEnv, (value.size * sizeOf<ByteVar>()).toULong(), byteArray as CValuesRef<COpaquePointerVar>, result.ptr)

    memcpy(byteArray[0], value.toCValues(), value.size.toULong())

    return result.value!!
}

/**
 * ArkTs 侧的 ArrayBuffer 转换为 ByteArray
 */
fun napi_value.fromArrayBuffer(): ByteArray {
    // Use memScoped to automatically manage the lifetime of allocated native memory
    return memScoped {
        val isArrayBuffer = alloc<BooleanVar>()

        // Check if the napi_value is an ArrayBuffer
        // The second argument of alloc<BooleanVar>() is implicitly initialized to false by default, but explicitly setting it might be clearer.
        isArrayBuffer.value = false
        napi_is_arraybuffer(OhosFFIManager.tlsEnv, this@fromArrayBuffer, isArrayBuffer.ptr)
        if (!isArrayBuffer.value) {
            throw RuntimeException("napi_value is not an ArrayBuffer")
        }

        val length = alloc<ULongVar>()
        val dataPtr = alloc<CPointerVar<ByteVar>>() // Allocate a CPointerVar to hold the void*

        // Get the array buffer's underlying data pointer and length
        // napi_get_arraybuffer_info expects void** for data.
        // In Kotlin Native, dataPtr.ptr gives CPointer<CPointerVar<ByteVar>>,
        // which correctly represents a pointer to a variable holding a CPointer<ByteVar> (void*).
        // We reinterpret it to COpaquePointerVar* (void**) to match the C signature.
        val status = napi_get_arraybuffer_info(OhosFFIManager.tlsEnv, this@fromArrayBuffer, dataPtr.ptr.reinterpret(), length.ptr)

        // Basic error handling for the N-API call
        if (status != napi_ok) {
            // You might want to get more detailed error info using napi_get_last_error_info
            throw RuntimeException("Failed to get ArrayBuffer info, status: $status")
        }

        val byteLength = length.value.toInt()

        // If the byte length is 0, return an empty ByteArray
        if (byteLength == 0) {
            return ByteArray(0)
        }

        val result = ByteArray(byteLength)

        val dataBuffer = dataPtr.value // Get the CPointer<ByteVar> (void*) to the buffer data

        if (dataBuffer != null) {
            // Use usePinned to get a stable pointer to the ByteArray's data for memcpy
            result.usePinned { pinned ->
                // Copy data from the N-API buffer to the Kotlin ByteArray using memcpy
                // memcpy(destination, source, num_bytes)
                memcpy(pinned.addressOf(0), dataBuffer, byteLength.toULong())
            }
        } else {
            // This case should ideally not happen if byteLength > 0 for a valid ArrayBuffer
            throw RuntimeException("ArrayBuffer data pointer is null despite having length $byteLength")
        }

        result
    } // memScoped automatically frees isArrayBuffer, length, and dataPtr
}


///**
// * ArkTs 侧的 Uint8Array 转换为 ByteArray
// */
//fun napi_value.fromUint8Array(): ByteArray {
//    val type = nativeHeap.alloc<napi_typedarray_type.Var>()
//    val length = nativeHeap.alloc<ULongVar>()
//    val inArrayBuffer = nativeHeap.alloc<napi_valueVar>()
//    val byteOffset = nativeHeap.alloc<ULongVar>()
//    napi_get_typedarray_info(OhosFFIManager.tlsEnv, this, type.ptr, length.ptr, null, inArrayBuffer.ptr, byteOffset.ptr)
//    if (type.value != napi_typedarray_type.napi_uint8_array) {
//        throw RuntimeException("not a Uint8Array")
//    }
//
//}
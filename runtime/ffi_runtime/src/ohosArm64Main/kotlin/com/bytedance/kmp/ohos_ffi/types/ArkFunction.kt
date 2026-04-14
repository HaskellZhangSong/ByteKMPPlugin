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
import com.bytedance.kmp.ohos_ffi.napi.call
import com.bytedance.kmp.ohos_ffi.napi.createFunction
import kotlinx.cinterop.*
import platform.ohos.napi.*

class ArkFunction(val receiver: ArkObject, val name: String, napiValue: napi_value): ArkObject(napiValue) {

    fun call(args: Array<ArkObject> = emptyArray()) {
        napiValue.call(receiver.napiValue, args.map { it.napiValue }.toTypedArray())
    }

    fun callForInstance(args: Array<ArkObject> = emptyArray()): ArkInstance {
        val result = napiValue.call(receiver.napiValue, args.map { it.napiValue }.toTypedArray())
        return ArkInstance(result)
    }

    fun callForArray(args: Array<ArkObject> = emptyArray()): ArkArray {
        val result = napiValue.call(receiver.napiValue, args.map { it.napiValue }.toTypedArray())
        return ArkArray(result)
    }

    fun callForPrimitive(args: Array<ArkObject> = emptyArray()): ArkPrimitive {
        val result = napiValue.call(receiver.napiValue, args.map { it.napiValue }.toTypedArray())
        return ArkPrimitive(result)
    }

    fun callForPromise(args: Array<ArkObject> = emptyArray(), then: napi_callback, catch: napi_callback? = null) {
        val promise = napiValue.call(receiver.napiValue, args.map { it.napiValue }.toTypedArray())
        val isPromise = nativeHeap.alloc<BooleanVar>()
        napi_is_promise(OhosFFIManager.tlsEnv, promise, isPromise.ptr)
        if (!isPromise.value) {
            throw RuntimeException("can not call `then` for object that is not a Promise")
        }
        val thenCallback = createFunction(then)
        promise.call("then", arrayOf(thenCallback))

        if (catch != null) {
            val catchCallback = createFunction(catch)
            promise.call("catch", arrayOf(catchCallback))
        }
    }
}
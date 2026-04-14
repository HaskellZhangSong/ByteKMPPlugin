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

package com.bytedance.kmp.ohos_ffi.promise

import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExport
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportClass
import com.bytedance.kmp.ohos_ffi.napi.call
import com.bytedance.kmp.ohos_ffi.napi.loadModule
import com.bytedance.kmp.ohos_ffi.napi.namedProperty
import com.bytedance.kmp.ohos_ffi.promise.js_bind_class.createJsObject
import com.bytedance.kmp.ohos_ffi.trace
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ohos.napi.napi_value

@KotlinExportClass
class PromiseCallback {

    var onResultListener: (napi_value) -> Unit = {}
    var onErrorListener: (String) -> Unit = {}

    @KotlinExport
    fun onResult(result: napi_value) {
        onResultListener(result)
    }

    @KotlinExport
    fun onError(result: String) {
        onErrorListener(result)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun getPromiseResult(promise: napi_value, onResult: (napi_value) -> Unit, onError: (String) -> Unit) {
    trace("ffi:promise_get_result") {
        val callback = PromiseCallback().apply {
            onResultListener = {
                onResult(it)
            }
            onErrorListener = {
                onError(it)
            }
        }
        loadModule(OhosFFIManager.harName).let {
            it.namedProperty("getPromiseResultForKN").call(it, arrayOf(promise, callback.createJsObject().napiValue))
        }
    }
}
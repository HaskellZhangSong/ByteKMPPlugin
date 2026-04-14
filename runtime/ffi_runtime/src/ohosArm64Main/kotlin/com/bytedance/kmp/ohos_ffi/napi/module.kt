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

package com.bytedance.kmp.ohos_ffi.napi

import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import com.bytedance.kmp.ohos_ffi.opt.napi_get_global_opt
import com.bytedance.kmp.ohos_ffi.opt.napi_load_module_with_info_opt
import com.bytedance.kmp.ohos_ffi.trace
import com.bytedance.kmp.ohos_ffi.types.ArkInstance
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.datetime.Clock
import platform.ohos.napi.napi_get_global
import platform.ohos.napi.napi_load_module_with_info
import platform.ohos.napi.napi_value
import platform.ohos.napi.napi_valueVar

val moduleCache = mutableMapOf<String, ArkInstance>()

fun loadModule(name: String): napi_value {
    trace("ffi:loadModule") {
        println("KmpLogger: start load module $name")
        val start = Clock.System.now().toEpochMilliseconds()
        return moduleCache.getOrPut(name) {
            ArkInstance(napi_load_module_with_info_opt(OhosFFIManager.tlsEnv, name, OhosFFIManager.bundleName) as napi_value)
        }.getNapiValue().also {
            val end = Clock.System.now().toEpochMilliseconds()
            println("KmpLogger: load module $name cost ${end - start} ms")
        }
    }
}

fun globalModule(): napi_value {
    return napi_get_global_opt(OhosFFIManager.tlsEnv) as napi_value
}
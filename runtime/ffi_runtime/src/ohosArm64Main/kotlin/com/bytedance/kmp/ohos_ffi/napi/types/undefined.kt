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

import platform.ohos.napi.*
import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import com.bytedance.kmp.ohos_ffi.opt.napi_get_undefined_opt
import kotlinx.cinterop.*

fun getUndefined(): napi_value {
    return napi_get_undefined_opt(OhosFFIManager.tlsEnv) as napi_value
}

fun napi_valueVar.isUndefined(): Boolean {
    return value!!.isUndefined()
}

fun napi_value.isUndefined(): Boolean {
    if (this == null) {
        return true
    }
    return com.bytedance.kmp.ohos_ffi.opt.isUndefined(OhosFFIManager.tlsEnv, this)
}
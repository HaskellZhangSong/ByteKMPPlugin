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
import com.bytedance.kmp.ohos_ffi.trace
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.value
import platform.ohos.napi.napi_value
import platform.ohos.napi.napi_valueVar

fun createString(value: String): napi_value {
    trace("ffi:createString"){
        return interpretCPointer(value.getNapiValue(OhosFFIManager.tlsEnv.rawValue))!!
    }
}

fun napi_valueVar.asString(): String {
    return value!!.asString()
}

fun napi_value.asString(): String {
    return napi_get_kotlin_string_utf16(OhosFFIManager.tlsEnv.rawValue, this.rawValue)
}

fun napi_value.asStringNullable(): String? {
    if (isUndefined()) {
        return null
    }
    return asString()
}
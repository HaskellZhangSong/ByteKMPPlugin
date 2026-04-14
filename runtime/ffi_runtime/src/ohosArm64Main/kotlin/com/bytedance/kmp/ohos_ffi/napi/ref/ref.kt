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

package com.bytedance.kmp.ohos_ffi.napi.ref

import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.ohos.napi.napi_create_reference
import platform.ohos.napi.napi_delete_reference
import platform.ohos.napi.napi_get_reference_value
import platform.ohos.napi.napi_ref
import platform.ohos.napi.napi_refVar
import platform.ohos.napi.napi_value
import platform.ohos.napi.napi_valueVar
import com.bytedance.kmp.ohos_ffi.opt.*

fun napi_valueVar.createRef(): napi_ref {
    return value!!.createRef()
}

fun napi_value.createRef(): napi_ref {
    return napi_create_reference_opt(OhosFFIManager.tlsEnv, this, 1) as napi_ref
}

fun napi_refVar.getRefValue(): napi_value {
    return value!!.getRefValue()
}

fun napi_ref.getRefValue(): napi_value {
    return napi_get_reference_value_opt(OhosFFIManager.tlsEnv, this) as napi_value
}

fun napi_refVar.getRefValueVar(): napi_valueVar {
    return value!!.getRefValueVar()
}

fun napi_ref.getRefValueVar(): napi_valueVar {
    val result: napi_valueVar = nativeHeap.alloc()
    napi_get_reference_value(OhosFFIManager.tlsEnv, this, result.ptr)
    return result
}

fun napi_refVar.deleteRef() {
    return value!!.deleteRef()
}

fun napi_ref.deleteRef() {
    napi_delete_reference_opt(OhosFFIManager.tlsEnv, this)
}
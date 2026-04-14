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
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.value
import platform.ohos.napi.*
import com.bytedance.kmp.ohos_ffi.opt.*
import kotlin.reflect.KClass
import kotlinx.cinterop.*
import com.bytedance.kmp.ohos_ffi.types.converter.*

/**
 * warming: 只能在 JS 含有 napi_env 线程调用
 */

inline fun invokeDirect(
    params: Array<out Any?>,
    kType: KClass<out Any>,
    jsCallback: ArkObjectSafeReference,
    recvJSValue: ArkObjectSafeReference?,
): Any? {
    // TODO: 慢路径的参数转换
    val paramsNapiValue = convertParamsToJSValueArray(params)
    val jsCb = jsCallback.handle
    val recv = recvJSValue?.handle
    memScoped {
        val exceptionVar = alloc<napi_valueVar>()
        val result = callFunction(
            OhosFFIManager.tlsEnv,
            recv,
            jsCb,
            paramsNapiValue.size,
            paramsNapiValue.toTypedArray().toCValues(),
            exceptionVar.ptr
        )
        val exceptionValue = exceptionVar.value
        if (exceptionValue == null) {
            // TODO: 慢路径的参数转换
            return jsValueToKTValue(OhosFFIManager.tlsEnv, result, kType)
        } else {
            throw RuntimeException("JavaScriptException")
//            throw JavaScriptException(exceptionValue)
        }
    }
}

inline fun convertParamsToJSValueArray(params: Array<out Any?>): List<napi_value?> {
    if (params.isEmpty()) {
        return emptyList()
    }
    return params.map {
        if (it == null) {
            null
        } else {
            ktValueToJSValue(OhosFFIManager.tlsEnv, it, it::class)
        }
    }
}

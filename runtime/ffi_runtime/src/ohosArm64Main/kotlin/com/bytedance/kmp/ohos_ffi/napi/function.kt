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
import com.bytedance.kmp.ohos_ffi.napi.types.asStringNullable
import kotlinx.cinterop.*
import platform.ohos.napi.*
import com.bytedance.kmp.ohos_ffi.opt.*
import com.bytedance.kmp.ohos_ffi.trace

fun napi_valueVar.call(funcName: String, args: Array<napi_value> = emptyArray()): napi_value {
    return value!!.call(funcName, args)
}

fun napi_value.call(funcName: String, args: Array<napi_value> = emptyArray()): napi_value {
    val func = namedProperty(funcName)
    return func.call(this, args)
}

fun napi_valueVar.call(receiver: napi_value, args: Array<napi_value> = emptyArray()): napi_value {
    return value!!.call(receiver, args)
}

fun napi_value.call(receiver: napi_value, args: Array<napi_value> = emptyArray()): napi_value {
    trace("ffi:ffi_call") {
        val argsValue = createNapiValueArray(OhosFFIManager.tlsEnv, args.size) as CArrayPointer<napi_valueVar>
        for (i in 0 until args.size) {
            argsValue[i] = args[i]
        }
        val result =  napi_call_function_opt(OhosFFIManager.tlsEnv, receiver, this, args.size, argsValue)
        if (result == null) {
            // 返回 null 的情况，可能是 ArkTs 代码发生了异常
            checkArkTsPendingException()
        }
        return result as napi_value
    }
}

fun checkArkTsPendingException() {
    val isPendingException = nativeHeap.alloc<BooleanVar>()
    napi_is_exception_pending(OhosFFIManager.tlsEnv, isPendingException.ptr)
    println("[checkArkTsPendingException] isPendingException: ${isPendingException.value}")
    if (isPendingException.value) {
        val lastException = nativeHeap.alloc<napi_valueVar>()
        val getStatus = napi_get_and_clear_last_exception(OhosFFIManager.tlsEnv, lastException.ptr)
        if (getStatus == napi_ok) {
            println("[checkArkTsPendingException] get exception status: $getStatus")
            val name = lastException.namedProperty("name").asStringNullable() ?: "unknown"
            val message = lastException.namedProperty("message").asStringNullable() ?: "unknown"
            val stack = lastException.namedProperty("stack").asStringNullable() ?: "unknown"
            println("[checkArkTsPendingException] name: $name\nmessage: $message\nstack: $stack")
            throw ArkTsException(name, message, stack)
        }
    }
}

class ArkTsException(name: String, _message: String, stack: String): RuntimeException(createMessage(name, _message, stack)) {
    companion object {
        fun createMessage(name: String, _message: String, stack: String): String {
            return "ArkTs代码发生崩溃，信息如下\nname: $name\nmessage: $_message\n$stack"
        }
    }
}

fun createFunction(func: napi_callback): napi_value {
    val cFunc = nativeHeap.alloc<napi_valueVar>()
    napi_create_function(OhosFFIManager.tlsEnv, "thenCallback", NAPI_AUTO_LENGTH, func, null, cFunc.ptr)
    return cFunc.value!!
}

fun napi_callback_info.params(size: Int): CArrayPointer<napi_valueVar> {

    return getParams(OhosFFIManager.tlsEnv, this, size) as CArrayPointer<napi_valueVar>
}

fun napi_callback_info.thisArg(): napi_value {
    return getThisArg(OhosFFIManager.tlsEnv, this) as napi_value
}

fun napi_callback_info.param(index: Int): napi_value {
    return params(index + 1)[index]!!
}
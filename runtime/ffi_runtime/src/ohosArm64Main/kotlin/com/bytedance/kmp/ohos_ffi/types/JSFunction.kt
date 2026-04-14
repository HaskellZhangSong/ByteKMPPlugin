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

import platform.ohos.napi.*
import com.bytedance.kmp.ohos_ffi.opt.*
import kotlin.reflect.KClass
import kotlinx.cinterop.*
import platform.posix.free
import com.bytedance.kmp.ohos_ffi.types.converter.*

class JSFunction(val env: napi_env?, val name: String, jsCallback: napi_value?) {
    var recvJSValue: ArkObjectSafeReference? = null
    val bindTid: Int = get_tid()
    val jsValue: ArkObjectSafeReference = ArkObjectSafeReference(env!!, jsCallback!!, bindTid)

    /**
     * bind This，JS 闭包 则可通过 this.XXX 访问
     */
    fun bind(env: napi_env?, recv: napi_value?) {
        recvJSValue = ArkObjectSafeReference(env!!, recv!!, bindTid)
    }

    inline fun <reified T : Any> invoke(vararg params: Any?): T? {
        val result = tsfnRegister.callSyncSafe(bindTid) {
            return@callSyncSafe invokeDirect(params, T::class)
        }
        return safeCaseNumberType(result, T::class) as T?
    }

    /**
     * Invoke direct
     * 避免旧版本inline调不通
     *
     * @param params
     * @param kType
     * @return
     */
    fun invokeDirect(params: Array<out Any?>, kType: KClass<out Any>): Any? {
        return invokeDirect(params, kType, jsValue, recvJSValue)
    }

}
/**
 * JS Value Number 类型映射为多个数值类型（Int, Long, Double）
 * 在已知参数类型 KClass 时，对类型进行转换
 */
fun safeCaseNumberType(value: Any?, type: KClass<out Any>): Any? {
    if (value !is Double) {
        return value
    }
    return when (type) {
        Int::class -> value.toInt()
        Long::class -> value.toLong()
        else -> value
    }

}

fun <T> convertJSCallbackInfoToKTParamList(
    env: napi_env?,
    callbackInfo: napi_callback_info?,
    paramsType: List<KClass<out Any>>? = null,
    offset: Int = 0
): MutableList<T?> {
        val jsParamsSize = getCallbackInfoParamsSize(env, callbackInfo)
        val params = getCbInfoWithSize(env, callbackInfo, jsParamsSize) ?: error("unknown params.")

        val paramsValue = mutableListOf<T?>()
        try {

            for (index in offset until jsParamsSize) {
                val kType = if (paramsType != null) {
                    paramsType[index - offset]
                } else {
                    ArkObjectSafeReference::class
                }
                val ktValue =
                    jsValueToKTValue(env, params[index], kType)
                paramsValue.add(ktValue as T?)
            }

        } finally {
            free(params)
        }
        return paramsValue
}


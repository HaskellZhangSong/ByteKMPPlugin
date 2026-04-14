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
package com.bytedance.kmp.ohos_ffi.types.converter


import platform.ohos.napi.*
import com.bytedance.kmp.ohos_ffi.opt.*
import kotlinx.cinterop.*
import kotlin.reflect.KClass
import com.bytedance.kmp.ohos_ffi.types.SafeHashMap
import com.bytedance.kmp.ohos_ffi.types.ArkObjectSafeReference
import com.bytedance.kmp.ohos_ffi.types.JSFunction

val ktFunctionMap = SafeHashMap<String, CallbackWrapper>()

class CallbackWrapper(var callback: ((args: Array<ArkObjectSafeReference?>) -> Any?)? = null)

fun Any.knoiDispose() {
    ktFunctionMap.remove(this.hashCode().toString())?.callback = null
}
val isDebug : Boolean = false;
class JSCallbackTypeConverter : TypeConverter<(args: Array<ArkObjectSafeReference?>) -> Any?> {
    override fun convertJSValueToKotlinValue(
        env: napi_env?,
        value: napi_value?
    ): (Array<ArkObjectSafeReference?>) -> Any? {
        val jsFunction = JSFunction(env, "", value)
        val funcWrapper: (args: Array<out Any?>) -> Any? = { args ->
            jsFunction.invoke<ArkObjectSafeReference>(*(args))
        }
        return funcWrapper
    }

    override fun getJSType(): napi_valuetype {
        return napi_valuetype.napi_function
    }

    override fun getKType(): KClass<out Any> = Function::class

    override fun convertKotlinValueToJSValue(
        env: napi_env?,
        value: ((args: Array<ArkObjectSafeReference?>) -> Any?)?
    ): napi_value? {
        if (value == null) {
            return null
        }
        val callbackWrapper = CallbackWrapper(value)
        val hashCode = value.hashCode().toString()
        val ptr = StableRef.create(callbackWrapper).asCPointer()
        ktFunctionMap.put(hashCode, callbackWrapper)

        // 仅 debug 需要排查泄露使用，采用统一名字减少字符串占用，
        val functionName = if (isDebug) "JSCallback@${hashCode}" else "JSCallback"
        // 采用 napi_create_function 创建匿名函数
        val jsFunction = createFunction(
            env,
            functionName,
            staticCFunction(::forwardJSCallback),
            ptr
        )

        wrap(
            env,
            jsFunction,
            ptr,
            staticCFunction(::finalizeJSCallback)
        )
        return jsFunction
    }
}

fun finalizeJSCallback(
    env: napi_env, data: COpaquePointer, hint: COpaquePointer
) {
    val ref = data.asStableRef<CallbackWrapper>()
    val callbackWrapper = ref.get()
    callbackWrapper.callback?.knoiDispose()
    ref.dispose()
}

internal fun forwardJSCallback(env: napi_env?, callbackInfo: napi_callback_info?): napi_value? {
    val ptr: COpaquePointer = getCbInfoData(env, callbackInfo) ?: return null
    val ref = ptr.asStableRef<CallbackWrapper>()
    val func = try {
        ref.get().callback
    } catch (e: Exception) {
        null
    }
    val result =
        func?.invoke(convertJSCallbackInfoToKTParamList<ArkObjectSafeReference>(env, callbackInfo).toTypedArray())
            ?: return null
    val typeConverter = getFirstSupportConverter(result::class)
    return ktValueToJSValue(env, result, typeConverter.getKType())
}


fun dumpAllJSCallback() {
    if (!isDebug) {
        return
    }
    println("JSCallback alive size: ${ktFunctionMap.size()}")
    if (ktFunctionMap.size() == 0) {
        return
    }
    var num = 1
    println("------- JSCallback alive list begin --------")
    ktFunctionMap.forEach {
        val func = it.value
        println("No ${num}: name=$func hash=${it.key}")
        num++
    }
    println("------- JSCallback alive list end --------")
}
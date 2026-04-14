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
import com.bytedance.kmp.ohos_ffi.annotation.ArkSoInitFunction
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import com.bytedance.kmp.ohos_ffi.opt.*
import platform.ohos.napi.*
import kotlin.native.concurrent.ThreadLocal
import kotlinx.cinterop.*


/**
 *  获取主线程 id
 */
val mainTid: Int = get_pid()

val tsfnRegister = ThreadSafeFunctionRegister()

@ArkSoInitFunction
fun registerTSFN(env: napi_env?, exports: napi_value) {
    tsfnRegister.registerThreadSafeFunctionIfNeed()
}

fun getTid(): Int {
    return get_tid()
}


class ThreadSafeFunctionRegister : SynchronizedObject() {

    private val tidToThreadSafeFunctionMap = mutableMapOf<Int, napi_threadsafe_function?>()

    /**
     * 注册 Thread Safe Function
     */
    fun registerThreadSafeFunctionIfNeed() {
        val tid = get_tid()
        synchronized(this) {
            if (tidToThreadSafeFunctionMap.containsKey(tid)) {
                return
            } else {
                tidToThreadSafeFunctionMap[tid] =
                    createThreadSafeFunctionWithSync(OhosFFIManager.tlsEnv, "tsfn-worker")
                println("register thread safe function success. tid = ${get_tid()}")
            }
        }
    }

    /**
     * 将 block 在 tid 的 JS 线程执行
     * @param tid 线程 ID
     * @param sync 是否同步调用
     * @param block 待执行的闭包
     * @return 返回值
     */
    fun callFunctionInOtherThread(
        tid: Int,
        sync: Boolean,
        block: () -> COpaquePointer?
    ): COpaquePointer? {
        val tsfn: napi_threadsafe_function = tidToThreadSafeFunctionMap[tid]
            ?: throw RuntimeException("thread safe function not register.")

        val ref = StableRef.create(block)
        return callThreadSafeFunction(
            tsfn, staticCFunction(::callbackInJSThread), ref.asCPointer(), sync, tid
        )
    }

    /**
     * 在 JS 线程在同步调用 闭包，并返回
     * @param tid JavaScript 线程 ID
     * @param block 待执行的闭包
     */
    inline fun <reified R : Any> callSyncSafe(
        tid: Int, crossinline block: () -> R?
    ): R? {
        if (get_tid() == tid) {
            return block.invoke()
        } else {
            val ptr = callFunctionInOtherThread(tid, true) {
                val result = block.invoke() ?: return@callFunctionInOtherThread null
                val ref = StableRef.create(result)
                ref.asCPointer()
            }
            val ref = ptr?.asStableRef<R>() ?: return null
            val result = ref.get()
            ref.dispose()
            return result
        }
    }

    /**
     * 在 tid 的 JS 线程异步调用 block
     * @param tid 线程 ID
     * @param block 带执行的闭包
     */
    fun callAsyncSafe(
        tid: Int, block: () -> Unit
    ) {
        if (get_tid() == tid) {
            return block.invoke()
        } else {
            callFunctionInOtherThread(tid, false) {
                block.invoke()
                return@callFunctionInOtherThread null
            }
        }
    }

}

fun callbackInJSThread(ptr: COpaquePointer?): COpaquePointer? {
    val dataRef = ptr?.asStableRef<() -> COpaquePointer?>() ?: return null
    val block = dataRef.get()
    dataRef.dispose()
    return block.invoke()
}

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

import kotlin.native.concurrent.Worker
import com.bytedance.kmp.ohos_ffi.opt.*
import kotlinx.cinterop.*

private var worker = Worker.start(false, "MainHandlerTimer")
private var blockToTimerIDMap: SafeHashMap<Int, () -> Unit> = SafeHashMap()

/**
 * 是否为主线程
 */
fun isMainThread(): Boolean {
    return get_tid() == mainTid
}

/**
 * 在主线程执行 block
 */
fun runOnMainThread(block: () -> Unit) {
    tsfnRegister.callAsyncSafe(mainTid) {
        block.invoke()
    }
}

/**
 * 取消执行 block
 */
fun cancelBlock(block: () -> Unit) {
    val timerID = blockToTimerIDMap.remove(block.hashCode())
    if (timerID == null) {
        println("timer maybe run or remove.")
    }
}

/**
 * 在主线程执行 block
 * @param block 待执行的闭包
 * @param delayMs 延迟的时间，单位为毫秒
 */
fun runOnMainThread(block: () -> Unit, delayMs: Long) {
    val hashcode = block.hashCode()
    blockToTimerIDMap.put(hashcode, block)
    worker.executeAfter(delayMs * 1000) {
        if (blockToTimerIDMap.get(hashcode) == null) {
            println("timer maybe run or remove.")
            return@executeAfter
        }
        blockToTimerIDMap.remove(hashcode)
        runOnMainThread(block)
    }
}
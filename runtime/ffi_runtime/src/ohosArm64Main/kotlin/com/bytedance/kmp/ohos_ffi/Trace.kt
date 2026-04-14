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

package com.bytedance.kmp.ohos_ffi


interface FFITraceProvider {
    fun beginSection(name: String)
    fun endSection()
}

/**
 * 空实现
 */
object FFIEmptyTraceProvider : FFITraceProvider {
    override fun beginSection(name: String) {

    }

    override fun endSection() {

    }
}

/**
 * 实现耗时打印
 */
object FFIDebugTraceProvider : FFITraceProvider {
    private val sectionStartTimes = mutableMapOf<String, Long>()

    override fun beginSection(name: String) {
        sectionStartTimes[name] = getTimeNanos()
    }

    override fun endSection() {
        val sectionName = sectionStartTimes.keys.lastOrNull() ?: return
        val startTime = sectionStartTimes.remove(sectionName) ?: return
        val elapsedNanos = getTimeNanos() - startTime
        val elapsedMicros = elapsedNanos / 1000
        val elapsedMillis = elapsedNanos / 1_000_000

        if (elapsedMillis >= 1) {
            println("[TRACE] $sectionName took ${elapsedMillis}ms")
        } else if (elapsedMicros >= 1) {
            println("[TRACE] $sectionName took ${elapsedMicros}μs")
        } else {
            println("[TRACE] $sectionName took ${elapsedNanos}ns")
        }
    }

    private fun getTimeNanos(): Long {
        return kotlin.system.getTimeNanos()
    }
}

object FFITraceManager {
    private var traceProvider: FFITraceProvider = FFIEmptyTraceProvider

    fun setTraceProvider(provider: FFITraceProvider) {
        traceProvider = provider
    }

    fun getTraceProvider(): FFITraceProvider = traceProvider
}


internal inline fun <T> trace(sectionName: String, block: () -> T): T {
    val traceProvider = FFITraceManager.getTraceProvider()
    traceProvider.beginSection(sectionName)
    try {
        return block()
    } finally {
        traceProvider.endSection()
    }
}
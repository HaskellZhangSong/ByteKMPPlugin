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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValues
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret
import platform.posix.dlopen
import platform.posix.dlsym
import kotlinx.cinterop.*

const val RTLD_NOW = 2
var beginTrace: CPointer<CFunction<(CValues<ByteVar>) -> Unit>>? = null
var endTrace: CPointer<CFunction<() -> Unit>>? = null

fun initTraceFuncIfNeed() {
//    if (isDebug) {       // 采用 dlsym 动态查找符号，因为 Kotlin Native 使用的 鸿蒙 SDK 版本为 sdk 9，无法正常链接。
//        val handle = dlopen("libhitrace_ndk.z.so", RTLD_NOW)
//        beginTrace = dlsym(handle, "OH_HiTrace_StartTrace")?.reinterpret()
//        endTrace = dlsym(handle, "OH_HiTrace_FinishTrace")?.reinterpret()
//        println("initTraceFunc beginTrace != null ${beginTrace != null}  endTrace != null ${endTrace != null}")
//    }
}

fun <T> trace(name: String, block: () -> T): T {
//    if (isDebug) {
//        return try {
//            beginTrace?.invoke("knoi:$name".cstr)
//            block()
//        } finally {
//            endTrace?.invoke()
//        }
//    } else {
        return block()
//    }
}
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

package com.bytedance.kmp.ohos_ffi

import kotlinx.cinterop.*
import platform.ohos.napi.*
import platform.ohos.uv.uv_loop_s

@ThreadLocal
private var threadLocalEnv: napi_env? = null

object OhosFFIManager {

    lateinit var harName: String // @byte/kmp
    @Deprecated("globalEnv即将废弃，请使用tlsEnv")
    lateinit var globalEnv: napi_env
    lateinit var bundleName: String

    val tlsEnv: napi_env
        get() = if (threadLocalEnv == null) {
            throw RuntimeException("tlsEnv has not been initialized")
        } else {
            threadLocalEnv!!
        }

    /**
     * 初始化, 缓存 env 和 bundle(com.ss.hm.ugc.aweme/entry)
     */
    fun init(env: napi_env, exports: napi_value, bundle: String, harName: String) {
        println("KmpLogger: bundle name $bundle")
        globalEnv = env     // FIXME: 如果从arkts worker/taskpool线程加载互操作模块，globalEnv 会被覆盖，可能会出现问题。
        threadLocalEnv = env
        bundleName = bundle
        this.harName = harName
    }
}
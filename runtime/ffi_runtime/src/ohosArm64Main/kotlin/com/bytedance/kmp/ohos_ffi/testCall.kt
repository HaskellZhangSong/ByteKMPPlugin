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

import com.bytedance.kmp.ohos_ffi.napi.param
import com.bytedance.kmp.ohos_ffi.napi.types.asString
import com.bytedance.kmp.ohos_ffi.types.ArkModule
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import platform.ohos.hilog.*
import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import kotlinx.coroutines.withContext
import platform.ohos.napi.napi_resolve_deferred

fun testCall() {
    ArkModule("libffi.so")
}

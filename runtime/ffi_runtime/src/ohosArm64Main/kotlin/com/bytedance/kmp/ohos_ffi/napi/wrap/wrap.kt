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

package com.bytedance.kmp.ohos_ffi.napi.wrap

import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import com.bytedance.kmp.ohos_ffi.opt.napi_unwrap_opt
import kotlinx.cinterop.*
import platform.ohos.napi.napi_value

fun napi_value.unwrap(): COpaquePointer? {
    return napi_unwrap_opt(OhosFFIManager.tlsEnv, this)
}
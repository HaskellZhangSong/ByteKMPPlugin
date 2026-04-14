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

@file:OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class)

package com.bytedance.kmp.ohos_ffi.types

import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import com.bytedance.kmp.ohos_ffi.debug.DebugRefCnt
import com.bytedance.kmp.ohos_ffi.napi.ref.createRef
import com.bytedance.kmp.ohos_ffi.napi.ref.deleteRef
import com.bytedance.kmp.ohos_ffi.napi.ref.getRefValue
import com.bytedance.kmp.ohos_ffi.napi.types.isUndefined
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.linux.free
import platform.ohos.napi.*
import kotlin.native.internal.createCleaner

open class ArkObject(var value: napi_value) {
    private var napiRef = value.createRef().also {
        DebugRefCnt.addArkRefCnt(this::class.simpleName ?: "ArkObject")
    }

    var napiValue: napi_value = value
        get() = napiRef.getRefValue()

    fun getNapiValue(): napi_value {
        return napiRef.getRefValue()
    }

    // 当前对象回收后主动解除 napiRef 的绑定
    @Suppress("unused") // Must be assigned
    private val cleaner = createCleaner(napiRef) {
        GlobalScope.launch(Dispatchers.Main) {
            it.deleteRef()
            DebugRefCnt.reduceArkRefCnt(this::class.simpleName ?: "ArkObject")
        }
    }

    fun isUndefined(): Boolean {
        return napiValue.isUndefined()
    }

}
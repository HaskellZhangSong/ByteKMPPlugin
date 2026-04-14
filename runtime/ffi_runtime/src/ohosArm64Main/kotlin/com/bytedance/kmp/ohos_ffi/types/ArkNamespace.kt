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

import com.bytedance.kmp.ohos_ffi.napi.createObject
import com.bytedance.kmp.ohos_ffi.napi.namedProperty
import com.bytedance.kmp.ohos_ffi.napi.setNamedProperty
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ohos.napi.*

class ArkNamespace(napiValue: napi_value): ArkObject(napiValue) {

    fun getExportClass(name: String): ArkClass {
        return ArkClass(this, name, napiValue.namedProperty(name))
    }

    fun getExportFunc(name: String): ArkFunction {
        return ArkFunction(this, name, napiValue.namedProperty(name))
    }

    fun getExportInstance(name: String): ArkInstance {
        return ArkInstance(napiValue.namedProperty(name), isExport = true, parent = this, exportName = name)
    }
}
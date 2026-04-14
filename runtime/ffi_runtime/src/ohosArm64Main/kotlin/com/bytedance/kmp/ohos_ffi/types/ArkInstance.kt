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

class ArkInstance(napiValue: napi_value,
                  val isExport: Boolean = false, // 是否为模块导出对象，反之是通过 new 手动创建的对象
                  val parent: ArkObject? = null, val exportName: String? = null, // 为模块导出对象时非空
                  val arkClass: ArkClass? = null): ArkObject(napiValue) {

    companion object {
        fun create(): ArkInstance {
            return ArkInstance(createObject())
        }
    }

    fun getFunction(name: String): ArkFunction {
        return ArkFunction(this, name, napiValue.namedProperty(name))
    }

    fun getProperty(name: String): ArkInstance {
        return ArkInstance(napiValue.namedProperty(name))
    }

    fun getArrayProperty(name: String): ArkArray {
        return ArkArray(napiValue.namedProperty(name))
    }

    fun getPrimitiveProperty(name: String): ArkPrimitive {
        return ArkPrimitive(napiValue.namedProperty(name))
    }

    fun setProperty(name: String, value: ArkObject) {
        napiValue.setNamedProperty(name, value.napiValue)
    }
}
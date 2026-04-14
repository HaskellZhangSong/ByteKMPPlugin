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

import com.bytedance.kmp.ohos_ffi.napi.types.asArray
import com.bytedance.kmp.ohos_ffi.napi.types.asInt
import com.bytedance.kmp.ohos_ffi.napi.types.asString
import com.bytedance.kmp.ohos_ffi.napi.types.createArray
import com.bytedance.kmp.ohos_ffi.napi.types.createIntArray
import com.bytedance.kmp.ohos_ffi.napi.types.createStringArray
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ohos.napi.*

class ArkArray(napiValue: napi_value): ArkObject(napiValue) {

    companion object {
        fun stringArray(data: Array<String>): ArkArray {
            return ArkArray(createStringArray(data))
        }

        fun intArray(data: Array<Int>): ArkArray {
            return ArkArray(createIntArray(data))
        }
    }

    fun asStringList(): List<String> {
        return asList {
            it.asString()
        }
    }

    fun asIntList(): List<Int> {
        return asList {
            it.asInt()
        }
    }

    fun asArkInstanceList(): List<ArkInstance> {
        return asList {
            ArkInstance(it)
        }
    }

    fun <T> asList(map: (napi_value) -> T): List<T> {
        return napiValue.asArray(map).toList()
    }

}
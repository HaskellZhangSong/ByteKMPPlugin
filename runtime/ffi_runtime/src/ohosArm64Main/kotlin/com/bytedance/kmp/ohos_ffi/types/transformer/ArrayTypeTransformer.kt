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

package com.bytedance.kmp.ohos_ffi.types.transformer

import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import com.bytedance.kmp.ohos_ffi.napi.types.asArray
import com.bytedance.kmp.ohos_ffi.opt.napi_create_array_opt
import com.bytedance.kmp.ohos_ffi.opt.napi_set_element_opt
import com.bytedance.kmp.ohos_ffi.transform.ArkTsExportCustomTransformer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ohos.napi.napi_value

// TODO ArkTS 侧的 Array 类型暂时都先映射为 Kotlin 的List类型。
class ArrayTypeTransformer<T>(val itemTransformer: ArkTsExportCustomTransformer<T>) : ArkTsExportCustomTransformer<List<T>> {
    override fun fromJsObject(value: napi_value): List<T> {
        return value.asArray {
            itemTransformer.fromJsObject(it)
        }.toList()
    }

    override fun toJsObject(value: List<T>): napi_value {
        val result = napi_create_array_opt(OhosFFIManager.tlsEnv) as napi_value
        value.forEachIndexed { i, v ->
            napi_set_element_opt(OhosFFIManager.tlsEnv, result, i, itemTransformer.toJsObject(v))
        }
        return result
    }
}
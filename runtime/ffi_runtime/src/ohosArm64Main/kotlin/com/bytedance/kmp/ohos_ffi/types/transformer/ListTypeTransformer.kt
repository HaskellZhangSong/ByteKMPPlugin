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

import com.bytedance.kmp.ohos_ffi.napi.call
import com.bytedance.kmp.ohos_ffi.napi.loadModule
import com.bytedance.kmp.ohos_ffi.napi.namedProperty
import com.bytedance.kmp.ohos_ffi.napi.newInstance
import com.bytedance.kmp.ohos_ffi.napi.types.asInt
import com.bytedance.kmp.ohos_ffi.napi.types.asList
import com.bytedance.kmp.ohos_ffi.napi.types.createInt
import com.bytedance.kmp.ohos_ffi.napi.types.createList
import com.bytedance.kmp.ohos_ffi.opt.getPropertyValue
import com.bytedance.kmp.ohos_ffi.transform.ArkTsExportCustomTransformer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ohos.napi.napi_env
import platform.ohos.napi.napi_value

/**
 * 对应 ArkTS 的 @ohos.util.List
 */
class ListTypeTransformer<T>(val itemTransformer: ArkTsExportCustomTransformer<T>) : ArkTsExportCustomTransformer<List<T>> {
    override fun fromJsObject(value: napi_value): List<T> {
        return value.asList {
            itemTransformer.fromJsObject(it)
        }
    }

    override fun toJsObject(value: List<T>): napi_value {
        return createList(value) {
            itemTransformer.toJsObject(it)
        }
    }
}
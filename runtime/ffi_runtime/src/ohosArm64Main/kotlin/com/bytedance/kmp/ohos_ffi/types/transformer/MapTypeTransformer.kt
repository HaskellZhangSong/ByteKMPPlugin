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
import com.bytedance.kmp.ohos_ffi.napi.globalModule
import com.bytedance.kmp.ohos_ffi.napi.namedProperty
import com.bytedance.kmp.ohos_ffi.napi.newInstance
import com.bytedance.kmp.ohos_ffi.napi.types.asBoolean
import com.bytedance.kmp.ohos_ffi.napi.types.asMap
import com.bytedance.kmp.ohos_ffi.napi.types.asString
import com.bytedance.kmp.ohos_ffi.napi.types.createMap
import com.bytedance.kmp.ohos_ffi.napi.types.createString
import com.bytedance.kmp.ohos_ffi.napi.types.isUndefined
import com.bytedance.kmp.ohos_ffi.transform.ArkTsExportCustomTransformer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ohos.napi.napi_env
import platform.ohos.napi.napi_value

/**
 * 对应 ArkTS global 里的 Map<K, V>
 */
class MapTypeTransformer<T>(val itemTransformer: ArkTsExportCustomTransformer<T>) : ArkTsExportCustomTransformer<Map<String, T>> {
    override fun fromJsObject(value: napi_value): Map<String, T> {
        return value.asMap {
            itemTransformer.fromJsObject(it)
        }
    }

    override fun toJsObject(value: Map<String, T>): napi_value {
        return createMap(value) {
            itemTransformer.toJsObject(it)
        }
    }
}
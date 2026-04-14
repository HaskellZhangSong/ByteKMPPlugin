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
package com.bytedance.kmp.ohos_ffi.types.converter


import platform.ohos.napi.*
import com.bytedance.kmp.ohos_ffi.opt.*
import kotlinx.cinterop.*
import kotlin.reflect.KClass
import com.bytedance.kmp.ohos_ffi.types.ArkObjectSafeReference

class JSValueTypeConverter : TypeConverter<ArkObjectSafeReference> {
    override fun convertJSValueToKotlinValue(env: napi_env?, value: napi_value?): ArkObjectSafeReference =
        ArkObjectSafeReference(env!!, value!!)

    override fun getKType(): KClass<out Any> = ArkObjectSafeReference::class

    override fun convertKotlinValueToJSValue(env: napi_env?, value: ArkObjectSafeReference?): napi_value? {
        if (value == null) {
            return null
        }
        return value.handle
    }

    override fun isSupportJSType(
        env: napi_env?,
        type: napi_valuetype,
        value: napi_value?
    ): Boolean {
        return getJSType() == type || napi_valuetype.napi_undefined == type
    }
}
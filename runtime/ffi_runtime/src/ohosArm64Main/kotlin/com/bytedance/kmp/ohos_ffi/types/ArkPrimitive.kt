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

import com.bytedance.kmp.ohos_ffi.napi.types.asBigInt
import com.bytedance.kmp.ohos_ffi.napi.types.asBoolean
import com.bytedance.kmp.ohos_ffi.napi.types.asDouble
import com.bytedance.kmp.ohos_ffi.napi.types.asInt
import com.bytedance.kmp.ohos_ffi.napi.types.asLong
import com.bytedance.kmp.ohos_ffi.napi.types.asString
import com.bytedance.kmp.ohos_ffi.napi.types.createBigInt
import com.bytedance.kmp.ohos_ffi.napi.types.createBoolean
import com.bytedance.kmp.ohos_ffi.napi.types.createDouble
import com.bytedance.kmp.ohos_ffi.napi.types.createInt
import com.bytedance.kmp.ohos_ffi.napi.types.createLong
import com.bytedance.kmp.ohos_ffi.napi.types.createString
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ohos.napi.*

enum class ArkPrimitiveType {
    BOOLEAN, DOUBLE, INT, LONG, STRING, BIG_INT, UNKNOWN
}

open class ArkPrimitive(napiValue: napi_value, val type: ArkPrimitiveType = ArkPrimitiveType.UNKNOWN): ArkObject(napiValue) {

    fun asBoolean(): Boolean {
        return napiValue.asBoolean()
    }

    fun asDouble(): Double {
        return napiValue.asDouble()
    }

    fun asInt(): Int {
        return napiValue.asInt()
    }

    fun asLong(): Long {
        return napiValue.asLong()
    }

    fun asBigInt(): Long {
        return napiValue.asBigInt()
    }

    fun asString(): String {
        return napiValue.asString()
    }
}

fun arkBoolean(content: Boolean): ArkPrimitive {
    return ArkPrimitive(createBoolean(content), ArkPrimitiveType.BOOLEAN)
}

fun arkDouble(content: Double): ArkPrimitive {
    return ArkPrimitive(createDouble(content), ArkPrimitiveType.DOUBLE)
}

fun arkInt(content: Int): ArkPrimitive {
    return ArkPrimitive(createInt(content), ArkPrimitiveType.INT)
}

fun arkLong(content: Long): ArkPrimitive {
    return ArkPrimitive(createLong(content), ArkPrimitiveType.LONG)
}

fun arkBigInt(content: Long): ArkPrimitive {
    return ArkPrimitive(createBigInt(content), ArkPrimitiveType.BIG_INT)
}

fun arkString(content: String): ArkPrimitive {
    return ArkPrimitive(createString(content), ArkPrimitiveType.STRING)
}
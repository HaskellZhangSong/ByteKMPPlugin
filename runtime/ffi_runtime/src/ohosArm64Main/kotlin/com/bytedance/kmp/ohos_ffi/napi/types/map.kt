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

package com.bytedance.kmp.ohos_ffi.napi.types

import com.bytedance.kmp.ohos_ffi.napi.call
import com.bytedance.kmp.ohos_ffi.napi.globalModule
import com.bytedance.kmp.ohos_ffi.napi.namedProperty
import com.bytedance.kmp.ohos_ffi.napi.newInstance

import kotlinx.cinterop.*
import platform.ohos.napi.*

fun createBooleanMap(data: Map<String, Boolean>): napi_value {
    return createMap(data) {
        createBoolean(it)
    }
}

fun createStringMap(data: Map<String, String>): napi_value {
    return createMap(data) {
        createString(it)
    }
}

fun createIntMap(data: Map<String, Int>): napi_value {
    return createMap(data) {
        createInt(it)
    }
}

fun createLongMap(data: Map<String, Long>): napi_value {
    return createMap(data) {
        createLong(it)
    }
}

fun createDoubleMap(data: Map<String, Double>): napi_value {
    return createMap(data) {
        createDouble(it)
    }
}

fun createFloatMap(data: Map<String, Float>): napi_value {
    return createMap(data) {
        createDouble(it.toDouble())
    }
}

fun <T> createMap(data: Map<String, T>, transformer: (T) -> napi_value): napi_value {
    val map = newInstance(globalModule().namedProperty("Map"))
    data.entries.forEach {
        map.call("set", arrayOf(createString(it.key), transformer(it.value)))
    }
    return map
}

fun napi_value.asBooleanMap(): Map<String, Boolean> {
    return asMap {
        it.asBoolean()
    }
}

fun napi_value.asStringMap(): Map<String, String> {
    return asMap {
        it.asString()
    }
}

fun napi_value.asIntMap(): Map<String, Int> {
    return asMap {
        it.asInt()
    }
}

fun napi_value.asLongMap(): Map<String, Long> {
    return asMap {
        it.asLong()
    }
}

fun napi_value.asDoubleMap(): Map<String, Double> {
    return asMap {
        it.asDouble()
    }
}

fun napi_value.asFloatMap(): Map<String, Float> {
    return asMap {
        it.asDouble().toFloat()
    }
}

fun <T> napi_value.asMap(transformer: (napi_value) -> T): Map<String, T> {
    val keysIterator = call("keys")
    val keys = mutableListOf<String>()
    while (true) {
        val next = keysIterator.call("next")
        val done = next.namedProperty("done")
        if (!done.isUndefined() && done.asBoolean()) {
            break
        }
        keys.add(next.namedProperty("value").asString())
    }
    return keys.map {
        val value = call("get", arrayOf(createString(it)))
        it to transformer(value)
    }.toMap()
}
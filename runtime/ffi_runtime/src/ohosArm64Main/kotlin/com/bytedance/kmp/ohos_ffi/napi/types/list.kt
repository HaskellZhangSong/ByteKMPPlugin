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
import com.bytedance.kmp.ohos_ffi.napi.loadModule
import com.bytedance.kmp.ohos_ffi.napi.namedProperty
import com.bytedance.kmp.ohos_ffi.napi.newInstance
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.value
import platform.ohos.napi.napi_value

fun createStringList(data: List<String>): napi_value {
    return createList(data) {
        createString(it)
    }
}

fun createIntList(data: List<Int>): napi_value {
    return createList(data) {
        createInt(it)
    }
}

fun createLongList(data: List<Long>): napi_value {
    return createList(data) {
        createLong(it)
    }
}

fun createDoubleList(data: List<Double>): napi_value {
    return createList(data) {
        createDouble(it)
    }
}

fun createFloatList(data: List<Float>): napi_value {
    return createList(data) {
        createDouble(it.toDouble())
    }
}

fun createBooleanList(data: List<Boolean>): napi_value {
    return createList(data) {
        createBoolean(it)
    }
}

fun <T> createList(data: List<T>, transformer: (T) -> napi_value): napi_value {
    val module = loadModule("@ohos.util.List")
    val list = newInstance(module)
    val addFunc = list.namedProperty("add")

    data.forEach {
        addFunc.call(list, arrayOf(transformer(it)))
    }

    return list
}

fun napi_value.asStringList(): List<String> {
    return asList {
        it.asString()
    }
}

fun napi_value.asIntList(): List<Int> {
    return asList {
        it.asInt()
    }
}

fun napi_value.asLongList(): List<Long> {
    return asList {
        it.asLong()
    }
}

fun napi_value.asDoubleList(): List<Double> {
    return asList {
        it.asDouble()
    }
}

fun napi_value.asFloatList(): List<Float> {
    return asList {
        it.asDouble().toFloat()
    }
}

fun napi_value.asBooleanList(): List<Boolean> {
    return asList {
        it.asBoolean()
    }
}


fun <T> napi_value.asList(transformer: (napi_value) -> T): List<T> {
    val result = mutableListOf<T>()

    val getFunc = namedProperty("get")
    val length = namedProperty("length").asInt()
    for (i in 0 until length) {
        val element = getFunc.call(this, arrayOf(createInt(i)))
        result.add(transformer(element))
    }

    return result
}
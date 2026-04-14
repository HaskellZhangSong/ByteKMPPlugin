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

import platform.ohos.napi.*
import com.bytedance.kmp.ohos_ffi.OhosFFIManager
import kotlinx.cinterop.*
import com.bytedance.kmp.ohos_ffi.opt.*
import com.bytedance.kmp.ohos_ffi.trace

fun createStringArray(array: Array<String>): napi_value {
    return createArray(array) {
        createString(it)
    }
}

fun createIntArray(array: Array<Int>): napi_value {
    return createArray(array) {
        createInt(it)
    }
}

fun createLongArray(array: Array<Long>): napi_value {
    return createArray(array) {
        createLong(it)
    }
}

fun createDoubleArray(array: Array<Double>): napi_value {
    return createArray(array) {
        createDouble(it)
    }
}

fun createFloatArray(array: Array<Float>): napi_value {
    return createArray(array) {
        createDouble(it.toDouble())
    }
}

fun createBooleanArray(array: Array<Boolean>): napi_value {
    return createArray(array) {
        createBoolean(it)
    }
}

fun <T> createArray(data: Array<T>, transformer: (T) -> napi_value): napi_value {
    trace("ffi:createArray"){
        val result = napi_create_array_opt(OhosFFIManager.tlsEnv) as napi_value
        data.forEachIndexed { i, v ->
            napi_set_element_opt(OhosFFIManager.tlsEnv, result, i, transformer(v))
        }
        return result
    }

}

fun napi_value.asStringArray(): Array<String> {
    return asArray {
        it.asString()
    }
}

fun napi_value.asIntArray(): Array<Int> {
    return asArray {
        it.asInt()
    }
}

fun napi_value.asLongArray(): Array<Long> {
    return asArray {
        it.asLong()
    }
}

fun napi_value.asDoubleArray(): Array<Double> {
    return asArray {
        it.asDouble()
    }
}

fun napi_value.asFloatArray(): Array<Float> {
    return asArray {
        it.asDouble().toFloat()
    }
}

fun napi_value.asBooleanArray(): Array<Boolean> {
    return asArray {
        it.asBoolean()
    }
}

fun <T> napi_valueVar.asArray(transformer: (napi_value) -> T): Array<T> {
    return value!!.asArray(transformer)
}

fun <T> napi_value.asArray(transformer: (napi_value) -> T): Array<T> {
    val length = napi_get_array_length_opt(OhosFFIManager.tlsEnv, this)
    println("KmpLogger: asArray ${length}")
    val result = Array(length) {
        val element = napi_get_element_opt(OhosFFIManager.tlsEnv, this, it) as napi_value
        transformer(element) as Any
    }
    return result as Array<T>

}
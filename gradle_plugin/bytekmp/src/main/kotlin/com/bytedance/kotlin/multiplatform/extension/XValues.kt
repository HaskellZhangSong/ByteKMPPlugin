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

package com.bytedance.kotlin.multiplatform.extension

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class ValueProvider<T>(
    private val defaultValue: T,
    private val onChanged: T.(value: T) -> Unit
) : ReadWriteProperty<Any?, T> {
    private var value: T = defaultValue
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        if (value != newValue) {
            value.onChanged(newValue)
            value = newValue
        }
    }
}

internal class NullableValueProvider<T>(
    private val onChanged: T?.(value: T?) -> Unit
)  {
    private var value: T? = null
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T?) {
        if (value != newValue) {
            value.onChanged(newValue)
        }
        value = newValue
    }
}

internal class NullableXValue<T>(
    private val onChanged: T?.(value: T?) -> Unit
) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): NullableValueProvider<T> {
        // getValue & setValue by 委托的类型
        // 调用 by CustomDelegate() 就会执行这个方法
        // 可以用来存储参数名(property.name)，以避免使用反射
        return NullableValueProvider(onChanged)
    }
}

internal class XValue<T>(
    private val defaultValue: T,
    private val onChanged: T.(value: T) -> Unit
) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ValueProvider<T> {
        // getValue & setValue by 委托的类型
        // 调用 by CustomDelegate() 就会执行这个方法
        // 可以用来存储参数名(property.name)，以避免使用反射
        return ValueProvider(defaultValue, onChanged)
    }
}

internal fun <T> xValue(defaultValue: T, onChanged: T.(value: T) -> Unit): XValue<T> {
    return XValue(defaultValue, onChanged)
}

internal fun <T> xValue(onChanged: T?.(value: T?) -> Unit): NullableXValue<T?> {
    return NullableXValue(onChanged)
}


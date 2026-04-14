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

import java.util.concurrent.CopyOnWriteArrayList

class ObservableValue<T>(
    defaultValue: T,
) {
    private var value: T = defaultValue
    private val observers: CopyOnWriteArrayList<(T) -> Unit> = CopyOnWriteArrayList()

    fun get() : T {
        return value
    }

    fun set(newValue: T) {
        if (value == newValue) return
        value = newValue
        observers.forEach { it(value) }
    }

    fun observe(observer : (T) -> Unit) {
        if (observers.contains(observer)) return
        observers.add(observer)
        observer(value)
    }


}
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

package com.bytedance.kmp.ohos_ffi.types

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

class SafeHashMap<K, V> : SynchronizedObject() {

    private val map = mutableMapOf<K, V>()
    fun put(key: K, value: V) {
        synchronized(this) {
            map.put(key, value)
        }
    }

    fun get(key: K): V? {
        synchronized(this) {
            return map[key]
        }
    }

    fun remove(key: K): V? {
        synchronized(this) {
            return map.remove(key)
        }
    }

    fun size(): Int {
        synchronized(this) {
            return map.size
        }
    }

    fun forEach(action: (Map.Entry<K, V>) -> Unit) {
        synchronized(this) {
            map.forEach(action)
        }
    }

    fun removeByValueOrNull(value: V): V? {
        return synchronized(this) {
            val key = map.entries.firstOrNull { it.value == value }?.key
            map.remove(key)
        }
    }

    fun containsValue(value: V): Boolean {
        return synchronized(this) {
            map.containsValue(value)
        }
    }
}
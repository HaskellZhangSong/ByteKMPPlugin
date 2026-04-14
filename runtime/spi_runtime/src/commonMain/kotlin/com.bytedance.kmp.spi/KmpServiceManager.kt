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

package com.bytedance.kmp.spi

import kotlin.reflect.KClass

expect object KmpServiceManager {

    fun <T : IKmpService> put(clazz: KClass<T>, impl: T)

    fun <T : IKmpService> put(clazz: KClass<T>, implList: List<T>)

    fun <T : IKmpService> get(clazz: KClass<T>): T?

    fun <T : IKmpService> getAll(clazz: KClass<T>): List<T>?
}

inline fun <reified T : IKmpService> kmpService() = KmpServiceManager.get(T::class)
inline fun <reified T : IKmpService> kmpServices() = KmpServiceManager.getAll(T::class)
inline fun <reified T : IKmpService> putKmpService(impl: T) = KmpServiceManager.put(T::class, impl)
inline fun <reified T : IKmpService> putKmpService(implList: List<T>) = KmpServiceManager.put(T::class, implList)
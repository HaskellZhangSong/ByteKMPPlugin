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

interface IKmpServiceManagerHost {
    fun <T : IKmpService> put(clazz: KClass<T>, impl: T)

    fun <T : IKmpService> put(clazz: KClass<T>, implList: List<T>)

    fun <T : IKmpService> get(clazz: KClass<T>): T?

    fun <T : IKmpService> getAll(clazz: KClass<T>): List<T>?
}

actual object KmpServiceManager {

    private var host: IKmpServiceManagerHost = KmpServiceManagerInternalImpl

    fun init(host: IKmpServiceManagerHost = KmpServiceManagerInternalImpl) {
        this.host = host
    }

    actual fun <T : IKmpService> put(clazz: KClass<T>, impl: T) {
        host.put(clazz, impl)
    }

    actual fun <T : IKmpService> put(clazz: KClass<T>, implList: List<T>) {
        host.put(clazz, implList)
    }

    actual fun <T : IKmpService> get(clazz: KClass<T>): T? {
        return host.get(clazz)
    }

    actual fun <T : IKmpService> getAll(clazz: KClass<T>): List<T>? {
        return host.getAll(clazz)
    }
}

/**
 * 默认实现，适用于宿主无 SPI 框架的场景
 */
private object KmpServiceManagerInternalImpl: IKmpServiceManagerHost {
    private val serviceMap = HashMap<String, MutableList<IKmpService>>()

    override fun <T : IKmpService> put(clazz: KClass<T>, impl: T) {
        serviceMap.getOrPut(generateKey(clazz)) {
            mutableListOf()
        }.add(impl)
    }

    override fun <T : IKmpService> put(clazz: KClass<T>, implList: List<T>) {
        serviceMap.getOrPut(generateKey(clazz)) {
            mutableListOf()
        }.addAll(implList)
    }

    override fun <T : IKmpService> get(clazz: KClass<T>): T? {
        return serviceMap.get(generateKey(clazz))?.first() as? T
    }

    override fun <T : IKmpService> getAll(clazz: KClass<T>): List<T>? {
        return serviceMap.get(generateKey(clazz)) as? List<T>
    }

    private fun <T : IKmpService> generateKey(clazz: KClass<T>): String {
        return clazz.qualifiedName ?: clazz.simpleName ?: ""
    }

}
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

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

open class ByteKMPExtension(private val project: Project) {

    val isIOS = isIOS(project.gradle)

    private fun isIOS(gradle: Gradle): Boolean {
        return gradle.startParameter.taskNames.any {
            it.contains("ios", ignoreCase = true) || it.contains("syncFramework")
        }
    }

    private val intelliJKMPExtension by lazy {
        project.extensions.getByName("kotlin") as? KotlinMultiplatformExtension ?: error("未获取到 kotlin 配置，请先配置 kotlin {} 插件")
    }

    internal var _isRootModule = ObservableValue(false)
    var isRootModule : Boolean
        get() {
            return _isRootModule.get()
        }
        set(value) {
            _isRootModule.set(value)
        }

    internal var _enabledCompose = ObservableValue(true)
    internal var enabledCompose : Boolean
        get() {
            return _enabledCompose.get()
        }
        set(value) {
            _enabledCompose.set(value)
        }

    internal var _enabledComposeResources = ObservableValue(true)
    internal var enabledComposeResources : Boolean
        get() {
            return _enabledComposeResources.get()
        }
        set(value) {
            _enabledComposeResources.set(value)
        }

    private var _serialization : SerializationExtension? = null
    val serialization: SerializationExtension
        get() {
            var serialization = _serialization
            if (serialization == null) {
                serialization = SerializationExtension(project, intelliJKMPExtension)
                _serialization = serialization
            }
            return serialization
        }

    fun serialization(action: Action<SerializationExtension>) {
        action.execute(serialization)
    }

    fun serialization(action: Closure<*>) {
        project.configure(serialization, action)
    }

    private var _compose: ComposeExtension? = null
    val compose: ComposeExtension
        get() {
            var compose = _compose
            if (compose == null) {
                compose = ComposeExtension(project, intelliJKMPExtension, this)
                _compose = compose
            }
            return compose
        }

    fun compose(action: Action<ComposeExtension>) {
        action.execute(compose)
    }

    fun compose(action: Closure<*>) {
        project.configure(compose, action)
    }

    private var _ffi: FFIExtension? = null
    val ffi: FFIExtension
        get() {
            var ffi = _ffi
            if (ffi == null) {
                ffi = FFIExtension(project, intelliJKMPExtension, this)
                _ffi = ffi
            }
            return ffi
        }

    fun ffi(action: Action<FFIExtension>) {
        action.execute(ffi)
    }

    fun ffi(action: Closure<*>) {
        project.configure(ffi, action)
    }

    private var _publish: PublishExtension ? = null
    val publish: PublishExtension
        get() {
            var publish = _publish
            if(publish == null) {
                publish = PublishExtension(project, intelliJKMPExtension, this)
                _publish = publish
            }
            return publish
        }

    fun publish(action: Action<PublishExtension>) {
        action.execute(publish)
    }

    fun publish(action: Closure<*>) {
        project.configure(publish, action)
    }

    private var _spi: SpiExtension? = null
    val spi: SpiExtension
        get() {
            var spi = _spi
            if(spi == null) {
                spi = SpiExtension(project, intelliJKMPExtension, this)
                _spi = spi
            }
            return spi
        }

    fun spi(action: Action<SpiExtension>) {
        action.execute(spi)
    }

    fun spi(action: Closure<*>) {
        project.configure(spi, action)
    }

    internal fun checkValid() {
        _serialization?.checkValid()
        _publish?.checkValid()
        _compose?.checkValid()
        _ffi?.checkValid()
    }

}
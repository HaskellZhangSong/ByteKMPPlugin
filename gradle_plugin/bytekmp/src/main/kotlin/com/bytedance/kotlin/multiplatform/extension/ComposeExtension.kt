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

import com.bytedance.kotlin.multiplatform.Versions
import com.bytedance.kotlin.multiplatform.applyIfAbsent
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.jetbrains.compose.resources.ResourcesExtension.ResourceClassGeneration
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ComposeExtension(
    private val project: Project,
    private val kmpExtension: KotlinMultiplatformExtension,
    private val parent: ByteKMPExtension
) {
    init {
        parent._enabledCompose.observe {
            applyComposeEnabled(it)
        }
    }

    var enabled: Boolean by xValue(false) {
        if (it) {
            parent.enabledCompose = it
        }
        if(this && !it) { // false -> true
            error("请勿反复设置 compose.enable 的值")
        }
    }

    var _resources: Resources? = null
    val resources: Resources
        get() {
            var resources = _resources
            if (resources == null) {
                resources = Resources()
                _resources = resources
            }
            return resources
        }

    fun resources(action: Action<Resources>) {
        action.execute(resources)
    }

    fun resources(configure : Closure<*>) {
        project.configure(resources, configure)
    }

    inner class Resources {
        init {
            parent._enabledComposeResources.observe {
                applyResourcesEnabled(it)
            }
        }
        var enabled: Boolean by xValue(false) {
            if (it) {
                require(this@ComposeExtension.enabled) { "compose 未启用，请先设置：compose { enable = true }" }
                parent.enabledComposeResources = it
            }
            if(this && !it) { // false -> true
                error("请勿反复修改 compose.resource.enable 的值")
            }
        }

        val auto = ResourceClassGeneration.Auto
        val always = ResourceClassGeneration.Always
        val never = ResourceClassGeneration.Never

        var publicResClass: Boolean
            get() {
                return getResourceExtension().publicResClass
            }
            set(value) {
                getResourceExtension().publicResClass = value
            }

        var packageOfResClass: String
            get() {
                return getResourceExtension().packageOfResClass
            }
            set(value) {
                getResourceExtension().packageOfResClass = value
            }

        var generateResClass: ResourceClassGeneration
            get() {
                return getResourceExtension().generateResClass
            }
            set(value) {
                getResourceExtension().generateResClass = value
            }

        var resourcePrefix: String
            get() {
                return getResourceExtension().resourcePrefix
            }
            set(value) {
                getResourceExtension().resourcePrefix = value
            }

        private fun getResourceExtension(): org.jetbrains.compose.resources.ResourcesExtension {
            val compose = project.extensions.findByName("compose") as org.jetbrains.compose.ComposeExtension
            return compose.extensions.findByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
        }

        private fun applyResourcesEnabled(enabled: Boolean) {
            if(enabled) {
                project.applyIfAbsent("org.jetbrains.compose")
                kmpExtension.apply {
                    sourceSets.commonMain.dependencies {
                        implementation("org.jetbrains.compose.components:components-resources:${Versions.COMPOSE_RESOURCES_VERSION}")
                    }
                }
            }
        }

    }

    private fun applyComposeEnabled(enabled: Boolean) {
        if(enabled) {
            project.applyIfAbsent("kotlin-composecompiler")
            kmpExtension.apply {
                sourceSets.commonMain.dependencies {
                    implementation("org.jetbrains.compose.runtime:runtime:${Versions.COMPOSE_VERSION}")
                }
            }
        }
        // 不会走到 false
    }

    fun checkValid() {
    }
}
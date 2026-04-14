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
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.*

class SpiExtension (
    private val project: Project,
    private val kmpExtension: KotlinMultiplatformExtension,
    private val parent: ByteKMPExtension
) {

    val android = "android"
    val ohosArm64 = "ohosArm64"
    val iosX64 = "iosX64"
    val iosArm64 = "iosArm64"
    val iosSimulatorArm64 = "iosSimulatorArm64"

    var enabledTarget: List<String> by xValue(emptyList()) {
        val targets = it.toMutableSet()
        if (parent.isIOS) {
            targets.remove(ohosArm64)
        }
        if (this.toTypedArray().contentEquals(targets.toTypedArray())) return@xValue
        if (this.isEmpty()) {
            project.applyIfAbsent("com.google.devtools.ksp")
            targets.forEach { targetName ->
                project.dependencies.apply {
                    add(
                        "ksp" + targetName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                        "com.bytedance.kmp.spi:processor:${Versions.CURRENT_VERSION}"
                    )
                }
            }
            // 添加 runtime
            kmpExtension.apply {
                sourceSets.commonMain.dependencies {
                    implementation("com.bytedance.kmp.spi:spi:${Versions.CURRENT_VERSION}")
                }
            }
            parent._isRootModule.observe { applyIsRootModule(it) }
        } else {
            error("请勿重复设置 spi.enableTarget 的值")
        }
    }

    fun enabledTarget(vararg target: String) {
        this.enabledTarget = target.toList()
    }

    private fun getKspExtension(): KspExtension {
        return project.extensions.findByName("ksp") as KspExtension
    }

    internal fun applyIsRootModule(isRootModule: Boolean) {
        if(isRootModule) {
            getKspExtension().arg("kmp_root_module", isRootModule.toString())
        }
    }

}
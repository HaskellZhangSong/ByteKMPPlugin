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

package com.bytedance.kotlin.multiplatform

import com.bytedance.kotlin.multiplatform.extension.ByteKMPExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

abstract class KotlinPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("Apply KotlinPlugin")
        target.applyIfAbsent("org.jetbrains.kotlin.multiplatform")
        val extension = target.extensions.create(
            "bytekmp",
            ByteKMPExtension::class.java,
            target
        )
        target.gradle.projectsEvaluated {
            val extension = target.extensions.findByName("bytekmp") as? ByteKMPExtension
            extension?.checkValid()
        }
        Versions.checkKotlinVersion(target)
    }
}
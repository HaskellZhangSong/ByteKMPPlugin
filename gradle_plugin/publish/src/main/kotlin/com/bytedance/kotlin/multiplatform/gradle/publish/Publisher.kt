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

package com.bytedance.kotlin.multiplatform.gradle.publish

import com.bytedance.kotlin.multiplatform.gradle.publish.KmpPublishingExtension.Companion.KMP_EXTENSION_NAME
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

object Publisher {
    fun initForProject(project: Project) {
        project.plugins.apply("maven-publish")
        val publishingExtension = project.extensions.findByName("publishing") as PublishingExtension
        PomManager.registerTask(project)
        project.extensions.create(KMP_EXTENSION_NAME, KmpPublishingExtension::class.java, project, publishingExtension)
//        val intelliJKMPExtension = project.extensions.getByName("kotlin") as? KotlinMultiplatformExtension ?: error("未获取到 kotlin 配置，请先配置 kotlin {} 插件")
        val intelliJKMPExtension = project.extensions.getByName("kotlin") as? KotlinMultiplatformExtension ?: error("未获取到 kotlin 配置，请先配置 kotlin {} 插件")
        intelliJKMPExtension.targets.all {
            if(it is KotlinAndroidTarget && it.name == "android") {
                it.publishLibraryVariants("release")
            }
        }
    }
}
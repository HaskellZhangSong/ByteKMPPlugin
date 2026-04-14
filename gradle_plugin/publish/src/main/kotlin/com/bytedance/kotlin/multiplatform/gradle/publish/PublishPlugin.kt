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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension

@Deprecated("请使用 ByteKMP 插件配置", ReplaceWith("KotlinPlugin", "com.bytedance.kotlin.multiplatform.KotlinPlugin"))
abstract class PublishPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        Publisher.initForProject(project)
    }
}


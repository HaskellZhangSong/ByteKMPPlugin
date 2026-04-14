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

package com.bytedance.kmp.ohos_ffi.har_bundle.task

import com.bytedance.kmp.ohos_ffi.har_bundle.BundleHarConfig
import com.bytedance.kmp.ohos_ffi.har_bundle.HarExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.SoHarGeneratorPlugin.Companion.EXTENSION_NAME
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.ISoHarGeneratorBeforeCompileProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@DisableCachingByDefault
open class PrepareCompileOhosTask : DefaultTask() {

    private lateinit var processors : List<ISoHarGeneratorBeforeCompileProcessor>

    @TaskAction
    open fun action() {
        val extension = project.extensions.findByName(EXTENSION_NAME) as HarExtension
        processors.forEach {
            it.run(project)
        }
    }

    class CreationAction(private val config: BundleHarConfig) : TaskCreationAction<PrepareCompileOhosTask> {
        override val name: String
            get() = "prepareCompileForOhos"
        override val type: Class<PrepareCompileOhosTask>
            get() = PrepareCompileOhosTask::class.java

        override fun configure(task: PrepareCompileOhosTask) {
            task.description = "prepare before build ohos so."
            task.processors = listOf()
        }

    }
}
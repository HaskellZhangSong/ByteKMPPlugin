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

package com.bytedance.kmp.ohos_ffi.har_bundle.processor

import com.bytedance.kmp.ohos_ffi.har_bundle.BundleHarConfig
import com.bytedance.kmp.ohos_ffi.har_bundle.HarExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.OhosFfiExtension
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

interface ISoHarGeneratorProcessor {
//    fun onPluginApply(project: Project, extension: HarExtension) {}

    fun onTaskCreate(soGenerateTask: Task) {}

    fun process(templateProjectDir: File, extension: BundleHarConfig)
}

interface ISoHarGeneratorBeforeCompileProcessor {
    fun run(project: Project) {}
}
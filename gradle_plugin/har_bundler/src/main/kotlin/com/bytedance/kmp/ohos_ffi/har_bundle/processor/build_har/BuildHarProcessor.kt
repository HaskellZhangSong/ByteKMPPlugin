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

package com.bytedance.kmp.ohos_ffi.har_bundle.processor.build_har

import com.bytedance.kmp.ohos_ffi.har_bundle.BundleHarConfig
import com.bytedance.kmp.ohos_ffi.har_bundle.HarExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.OhosFfiExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.ISoHarGeneratorProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.basic.BasicProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.FileDownloader
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.createNewFile
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

class BuildHarProcessor: ISoHarGeneratorProcessor {

    private lateinit var project: Project
    private lateinit var extension: BundleHarConfig
    private lateinit var soName: String
    private lateinit var harName: String
    private lateinit var harVersion: String
    private lateinit var rootModuleName: String

    override fun onTaskCreate(soGenerateTask: Task) {
        this.project = soGenerateTask.project
    }

    override fun process(templateProjectDir: File, extension: BundleHarConfig) {
        this.extension = extension
        this.soName = extension.soName!!
        this.harName = extension.harName!!
        this.harVersion = extension.harVersion!!
        this.rootModuleName = extension.rootModuleName!!
        buildHar(templateProjectDir)
    }

    private fun buildHar(templateProjectDir: File) {
        project.exec {
            it.workingDir = templateProjectDir
            val hvigorCommand = if (extension.useSystemPathForHvigorw) {
                arrayOf("hvigorw")
            } else {
                if (!File(extension.ideDir!!).exists()) {
                    error("未找到 deveco / hvigrow 位置，请在 local.properties 中设置 deveco.dir=你的deveco地址")
                }
                it.environment("DEVECO_SDK_HOME", "${extension.ideDir}/Contents/sdk")
                arrayOf("${extension.ideDir}/Contents/tools/node/bin/node",
                    "${extension.ideDir}/Contents/tools/hvigor/bin/hvigorw.js")
            }
            it.commandLine(
                *hvigorCommand,
                "--mode",
                "module",
                "-p",
                "product=default",
                "-p",
                "buildMode=${extension.buildMode}",
                "-p",
                "module=${soName}@default",
                "assembleHar",
                "--analyze=normal"
            )
        }
        if (!extension.localHarPath.isNullOrEmpty()) {
            val dir = if (extension.localHarPath!!.startsWith("/")) {
                File(extension.localHarPath)
            } else {
                File(project.rootDir.path, extension.localHarPath)
            }
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val harFile = File(templateProjectDir, "$soName/build/default/outputs/default/$soName.har")
            harFile.copyTo(File(dir, harFile.name), true)
        }
    }

}
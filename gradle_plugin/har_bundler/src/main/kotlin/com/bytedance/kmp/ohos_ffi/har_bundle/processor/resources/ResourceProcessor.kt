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

package com.bytedance.kmp.ohos_ffi.har_bundle.processor.resources

import com.bytedance.kmp.ohos_ffi.har_bundle.BundleHarConfig
import com.bytedance.kmp.ohos_ffi.har_bundle.HarExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.OhosFfiExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.ISoHarGeneratorProcessor
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

/**
 * desc: Compose 资源文件处理打包
 */
class ResourceProcessor : ISoHarGeneratorProcessor {

    private lateinit var project: Project
    private lateinit var destinationResourceDir: File

    private val processResourcesTaskName = "ohosArm64ProcessResources"
    private val kmpResourceDir = "processedResources/ohosArm64/main/composeResources"

    override fun onTaskCreate(soGenerateTask: Task) {
        this.project = soGenerateTask.project
        destinationResourceDir = project.layout.buildDirectory.dir(kmpResourceDir).get().asFile
        // 依赖 compose 资源打包
        soGenerateTask.dependsOn(processResourcesTaskName)
    }

    override fun process(templateProjectDir: File, extension: BundleHarConfig) {
        val sourceFileDir = File(destinationResourceDir.absolutePath)

        if (sourceFileDir.exists() && sourceFileDir.isDirectory) {
            if (!extension.enableOhosResourceCompress) {
                // 移动到 rawfile 目录下
                val rawfile = "${templateProjectDir.absolutePath}/${extension.soName!!}/src/main/resources/rawfile/composeResources"
                sourceFileDir.copyRecursively(File(rawfile), true)
                println("[composeResource] move compose resource to $rawfile")
                return
            }


            val desResourceDir = "${templateProjectDir.absolutePath}/${extension.soName!!}/src/main/resources"
            val rawFileDir = "${desResourceDir}/rawfile/composeResources"
            //type -> id -> resource item

            // 找到各个模块的一级资源目录
            val topLevelResourceDir = sourceFileDir.walkTopDown()
                .filter { it.isDirectory }
                .filter { !it.listFiles().isNullOrEmpty() }
                .filter { it.path.endsWith("generated.resources") }
                .toList()

            project.copyFilesResourceToRawFile(
                topLevelResourceDir,
                rawFileDir
            )

            val resourceRefItems = project.handleAndCopyDrawableAndValuesResource(topLevelResourceDir, desResourceDir)

            val resourceRefArktsFilePath = "${templateProjectDir.absolutePath}/${extension.soName!!}/src/main/ets/KmpResourceRef.ets"
            val indexFilePath = "${templateProjectDir.absolutePath}/${extension.soName!!}/Index.ets"

            // 兼容无用资源插件，告诉 ArkTS 侧资源被使用了
            project.writeKMPResourceRef(
                File(resourceRefArktsFilePath),
                File(indexFilePath),
                resourceRefItems
            )
        }
    }
}
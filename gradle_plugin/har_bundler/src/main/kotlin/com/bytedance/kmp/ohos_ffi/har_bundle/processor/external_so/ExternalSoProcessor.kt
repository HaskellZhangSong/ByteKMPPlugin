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

package com.bytedance.kmp.ohos_ffi.har_bundle.processor.external_so

import com.bytedance.kmp.ohos_ffi.har_bundle.BundleHarConfig
import com.bytedance.kmp.ohos_ffi.har_bundle.HarExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.OhosFfiExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.ISoHarGeneratorProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.rewrite
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.util.PatternSet
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

class ExternalSoProcessor : ISoHarGeneratorProcessor {

    private lateinit var project: Project
    private lateinit var extension: BundleHarConfig

    private val includeSoList by lazy {
        project.buildDir.resolve(getLinkOutputDir()).resolve("include").walkTopDown().filter { it.extension == "so" }
            .toList()
    }

    private fun getLinkOutputDir(): String {
        return if (extension.buildMode == "debug") {
            "bin/ohosArm64/debugShared"
        } else {
            "bin/ohosArm64/releaseShared"
        }
    }

    override fun onTaskCreate(soGenerateTask: Task) {
        this.project = soGenerateTask.project
    }


    override fun process(templateProjectDir: File, config: BundleHarConfig) {
        this.extension = config
        val moduleFile = File(templateProjectDir, extension.soName)
        val patterns = PatternSet().apply {
            exclude(extension.excludeSoName)
        }
        // 原始 so 列表
        val soFileList = getSoFileList(project, extension.externalSoList)
        val soFiles = project.objects.fileCollection().from(soFileList).from(includeSoList)
        // 过滤后的 so 列表
        val filteredSoFiles = project.objects.fileCollection().from(
            { soFiles.asFileTree.matching(patterns) }
        )

        val targetDir = moduleFile.resolve("libs/arm64-v8a")
        filteredSoFiles.forEach { soFile ->
            // copy 文件
            soFile.copyTo(targetDir.resolve(soFile.name), true)
            // 修改 CMakeLists.txt
            File(moduleFile, "src/main/cpp/CMakeLists.txt").rewrite {
                "$it\ntarget_link_libraries(${extension.soName} PUBLIC " + "$" + "{ENTRY_ROOT_PATH}/libs/arm64-v8a/${soFile.name})"
            }
        }

    }

    companion object {
        private fun libraryName(soFile: File): String {
            return soFile.nameWithoutExtension.removePrefix("lib")
        }

        private fun getSoFileList(project: Project, soList: List<String>): List<File> {
            return soList.flatMap {
                val file = project.file(it)
                when {
                    file.isDirectory -> file.walkTopDown().filter { it.isFile && it.extension == "so" }.toList()
                    file.isFile && file.extension == "so" -> listOf(file)
                    else -> emptyList()
                }
            }.distinctBy { it.canonicalPath }
        }

        internal fun onPluginApply(project: Project, extension: HarExtension) {
            val soFileList = getSoFileList(project, extension.externalSoList)

            project.gradle.projectsEvaluated {
                project.extensions.configure(KotlinMultiplatformExtension::class.java) {
                    it.targets.filterIsInstance<KotlinNativeTarget>().forEach {
                        it.binaries.all {
                            val dirs = soFileList.map { it.parentFile.canonicalPath }.distinct()
                            val soName = soFileList.map { libraryName(it) }.distinct()

                            dirs.forEach { path ->
                                it.linkerOpts("-L$path")
                                println("add linker opts -L$path")
                            }
                            soName.forEach { libraryName ->
                                it.linkerOpts("-l$libraryName")
                                println("add linker opts -l$libraryName")
                            }
                        }
                    }
                }
            }
        }
    }
}
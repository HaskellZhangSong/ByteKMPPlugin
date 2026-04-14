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

package com.bytedance.kmp.ohos_ffi.har_bundle

import com.bytedance.kmp.ohos_ffi.har_bundle.processor.basic.BasicProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.build_har.BuildHarProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.compose.ComposeProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.external_so.ExternalSoProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.resources.ResourceProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.task.BundlerHarTask
import com.bytedance.kmp.ohos_ffi.har_bundle.task.PrepareCompileOhosTask
import com.bytedance.kmp.ohos_ffi.har_bundle.task.registerTask
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.configureOhosTarget
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.createNewDir
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.renameDirectory
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.LogLevel
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind
import java.io.File
import java.net.JarURLConnection
import java.util.*

@Deprecated("请使用 ByteKMP 插件配置", ReplaceWith("KotlinPlugin", "com.bytedance.kotlin.multiplatform.KotlinPlugin"))
class SoHarGeneratorPlugin : Plugin<Project> {

    companion object {
        const val COMPILE_TASK_DEBUG = "linkDebugSharedOhosArm64"
        const val COMPILE_TASK_RELEASE = "linkReleaseSharedOhosArm64"
        const val HARMONY_TEMPLATE_PROJECT_ZIP_FILE = "KmpTemplate.zip"
        const val EXTENSION_NAME = "soHarGenerator"
    }

    private val processors = mutableListOf(
        // 保证 BasicProcessor 在前面
        BasicProcessor(),
        ExternalSoProcessor(),
        ResourceProcessor(),
        ComposeProcessor(),
        // 保证 BuildSoProcessor 在最后
        BuildHarProcessor()
    )

    private fun isIOS(gradle: Gradle): Boolean {
        return gradle.startParameter.taskNames.any {
            it.contains("ios", ignoreCase = true) || it.contains("syncFramework")
        }
    }

    override fun apply(applyProject: Project) {
        val extension = applyProject.extensions.create(EXTENSION_NAME, HarExtension::class.java)

        if (isIOS(applyProject.gradle)) {
            return
        }

        ExternalSoProcessor.onPluginApply(applyProject, extension)

        applyProject.gradle.projectsEvaluated {
            extension.check()
            ExternalSoProcessor.onPluginApply(applyProject, extension)

            // prepare
            val prepareCompileOhosTask = applyProject.registerTask(
                PrepareCompileOhosTask.CreationAction(BundleHarConfig().copyForm(extension))
            )

            val kotlin : KotlinMultiplatformExtension = applyProject.project.extensions.findByName("kotlin") as KotlinMultiplatformExtension
            // link task 依赖当前 task
            kotlin.configureOhosTarget {
                binaries.all { binary ->
                    if(binary.outputKind == NativeOutputKind.DYNAMIC) {
                        binary.compilation.compileTaskProvider.configure { it.dependsOn(prepareCompileOhosTask) }
                    }
                }
            }
            listOf("debug", "release").forEach {
                val config = BundleHarConfig().copyForm(extension).apply {
                    this.buildMode = it
                }
                applyProject.registerTask(
                    BundlerHarTask.CreationAction(config)
                )
            }

            // 即将删除
            applyProject.tasks.register("generateSoHar") {
                it.apply {
                    group = "so har generate"
                    if(extension.generateLocalHarOnly) {
                        if(extension.buildMode == "debug") {
                            dependsOn("bundleDebugHar")
                        } else {
                            dependsOn("bundleReleaseHar")
                        }
                    } else {
                        if(extension.buildMode == "debug") {
                            dependsOn("publishDebugHar")
                        } else {
                            dependsOn("publishReleaseHar")
                        }
                    }
                    it.doLast{
                        logger.error("generateSoHar 即将废弃，请使用 bundleDebugHar/bundleReleaseHar")
                    }
                }
            }
        }
    }

//    private fun getTemplateProjectDir(applyProject: Project, extension: BundleHarConfig): File {
//        val devecoCacheDir = createNewDir(applyProject.buildDir, "ohos_ffi_tmp")
//
//        // 本地文件模板
//        if (extension.templateDir != null) {
//            val dir = if (extension.templateDir!!.startsWith("/")) {
//                File(extension.templateDir)
//            } else {
//                File(applyProject.rootDir.path, extension.templateDir)
//            }
//            val result = File(devecoCacheDir, dir.name)
//            dir.copyRecursively(result)
//            return result
//        }
//        // 获取插件自带的模板
//        val zipFile = getTemplateZipFile(devecoCacheDir)
//        applyProject.exec {
//            it.commandLine("unzip", zipFile.absoluteFile, "-d", devecoCacheDir.absoluteFile)
//        }
//        return File(devecoCacheDir, HARMONY_TEMPLATE_PROJECT_ZIP_FILE.split(".").first())
//    }
//
//    private fun getTemplateZipFile(dir: File): File {
//        val resourceUrl = this::class.java.classLoader.getResource(HARMONY_TEMPLATE_PROJECT_ZIP_FILE)
//        if (resourceUrl == null) {
//            error("找不到模板文件")
//        }
//
//        // 根据协议类型，处理不同的情况（JAR包或文件系统）
//        val inputStream = if (resourceUrl.protocol == "jar") {
//            // 如果在JAR包中，通过 URLConnection 获取 InputStream
//            val urlConnection = resourceUrl.openConnection() as JarURLConnection
//            urlConnection.jarFile.getInputStream(urlConnection.jarEntry)
//        } else {
//            // 如果在文件系统中，直接通过 File 获取 InputStream
//            File(resourceUrl.toURI()).inputStream()
//        }
//
//        val outputFile = File(dir, HARMONY_TEMPLATE_PROJECT_ZIP_FILE)
//
//        inputStream.use {
//            // 使用 Files.copy 复制流到目标文件
//            it.copyTo(outputFile.outputStream())
//        }
//
//        return outputFile
//    }
}


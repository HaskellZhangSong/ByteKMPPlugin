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
import com.bytedance.kmp.ohos_ffi.har_bundle.SoHarGeneratorPlugin
import com.bytedance.kmp.ohos_ffi.har_bundle.SoHarGeneratorPlugin.Companion
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.ISoHarGeneratorProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.basic.BasicProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.build_har.BuildHarProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.compose.ComposeProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.external_so.ExternalSoProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.resources.ResourceProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.createNewDir
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.renameDirectory
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.JarURLConnection
import java.util.*

open class BundlerHarTask : DefaultTask() {

    companion object {
        internal const val DEVECO_CACHE_DIR = "ohos_ffi_tmp"
        internal const val COMPILE_TASK_DEBUG = "linkDebugSharedOhosArm64"
        internal const val COMPILE_TASK_RELEASE = "linkReleaseSharedOhosArm64"
    }

    private lateinit var config: BundleHarConfig
    private lateinit var processors : List<ISoHarGeneratorProcessor>

    @TaskAction
    open fun doAction() {
        var templateProjectDir = getTemplateProjectDir(project, config)
        // 给模板项目随机重命名，deveco 处理 同一个名称的项目会有 bug
        templateProjectDir = renameDirectory(templateProjectDir, config.templateProjectDir!!)
        // 修改模板代码并生成 har
        processors.forEach {
            it.process(templateProjectDir, config)
        }
        logger.log(LogLevel.LIFECYCLE, "\n\u001b[32m Bundle har: ${config.harName} Complete. \u001b[0m")
    }

    private fun getTemplateProjectDir(applyProject: Project, config: BundleHarConfig): File {
        val devecoCacheDir = createNewDir(config.templateCacheDir!!)

        // 本地文件模板
        if (config.templateDir != null) {
            val dir = if (config.templateDir!!.startsWith("/")) {
                File(config.templateDir)
            } else {
                File(applyProject.rootDir.path, config.templateDir)
            }
            val result = File(devecoCacheDir, dir.name)
            dir.copyRecursively(result)
            return result
        }
        // 获取插件自带的模板
        val zipFile = getTemplateZipFile(devecoCacheDir)
        // TODO 直接用 Unzip task
        applyProject.exec {
            it.commandLine("unzip", zipFile.absoluteFile, "-d", devecoCacheDir.absoluteFile)
        }
        return File(devecoCacheDir, SoHarGeneratorPlugin.HARMONY_TEMPLATE_PROJECT_ZIP_FILE.split(".").first())
    }

    private fun getTemplateZipFile(dir: File): File {
        val resourceUrl = this::class.java.classLoader.getResource(SoHarGeneratorPlugin.HARMONY_TEMPLATE_PROJECT_ZIP_FILE)
        if (resourceUrl == null) {
            error("找不到模板文件")
        }

        // 根据协议类型，处理不同的情况（JAR包或文件系统）
        val inputStream = if (resourceUrl.protocol == "jar") {
            // 如果在JAR包中，通过 URLConnection 获取 InputStream
            val urlConnection = resourceUrl.openConnection() as JarURLConnection
            urlConnection.jarFile.getInputStream(urlConnection.jarEntry)
        } else {
            // 如果在文件系统中，直接通过 File 获取 InputStream
            File(resourceUrl.toURI()).inputStream()
        }

        val outputFile = File(dir, SoHarGeneratorPlugin.HARMONY_TEMPLATE_PROJECT_ZIP_FILE)

        inputStream.use {
            // 使用 Files.copy 复制流到目标文件
            it.copyTo(outputFile.outputStream())
        }

        return outputFile
    }

    class CreationAction(private val config: BundleHarConfig) : TaskCreationAction<BundlerHarTask> {
        override val name: String
            get() = "bundle${config.buildMode.capitalize(Locale.getDefault())}Har"
        override val type: Class<BundlerHarTask>
            get() = BundlerHarTask::class.java

        override fun configure(task: BundlerHarTask) {
            task.group = "har"
            val timestamp = System.currentTimeMillis()
            config.templateCacheDir = task.project.buildDir.resolve(DEVECO_CACHE_DIR).resolve(config.buildMode.lowercase())
            config.templateProjectDir = config.templateCacheDir!!.resolve("${DEVECO_CACHE_DIR}_${timestamp}")
            val compileTaskName = if (config.buildMode == "debug") {
                COMPILE_TASK_DEBUG
            } else {
                COMPILE_TASK_RELEASE
            }
            task.config = config
            task.dependsOn(compileTaskName)
            task.processors = listOf(
                // 保证 BasicProcessor 在前面
                BasicProcessor(),
                ExternalSoProcessor(),
                ResourceProcessor(),
                ComposeProcessor(),
                // 保证 BuildSoProcessor 在最后
                BuildHarProcessor()
            )

            task.processors.forEach {
                it.onTaskCreate(task)
            }
        }


    }
}
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

package com.bytedance.kmp.ohos_ffi.har_bundle.processor.basic

import com.bytedance.kmp.ohos_ffi.har_bundle.BundleHarConfig
import com.bytedance.kmp.ohos_ffi.har_bundle.HarExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.OhosFfiExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.ISoHarGeneratorProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.createNewFile
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.renameDirectory
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.rewrite
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

class BasicProcessor: ISoHarGeneratorProcessor {

    companion object {
        const val ORIGIN_MODULE_NAME = "kmp"
        const val ORIGIN_HAR_NAME = "@byte/kmp"
    }

    private lateinit var project: Project
    private lateinit var extension: BundleHarConfig
    private lateinit var soName: String
    private lateinit var harName: String
    private lateinit var harVersion: String
    private lateinit var rootModuleName: String

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

    override fun process(templateProjectDir: File, extension: BundleHarConfig) {
        this.extension = extension
        this.soName = extension.soName!!
        this.harName = extension.harName!!
        this.harVersion = extension.harVersion!!
        this.rootModuleName = extension.rootModuleName!!

        // 修改根目录 build-profile.json5
        File(templateProjectDir, "build-profile.json5").rewrite {
            it.replace("\"$ORIGIN_MODULE_NAME\"", "\"$soName\"")
                .replace("\"./$ORIGIN_MODULE_NAME\"", "\"./$soName\"")
        }

        // 模块重命名
        val moduleFile = renameDirectory(File(templateProjectDir, ORIGIN_MODULE_NAME), soName)

        // 删除 empty 文件
        File(moduleFile, "libs/arm64-v8a/empty").apply {
            if (exists()) {
                delete()
            }
        }
        File(moduleFile, "src/main/cpp/include/empty").apply {
            if (exists()) {
                delete()
            }
        }

        val shouldStrip = extension.stripSymbol ?: extension.buildMode == "release"
        if (shouldStrip) {

            // 修改 kmp 模块下 build-profile.json5,设置 strip = true
            File(moduleFile, "build-profile.json5").rewrite {
                it.replace("\"strip\": false,", "\"strip\": true,")
            }
            // 修改 CMakeLists.txt，添加 -g -Oz
            File(moduleFile, "src/main/cpp/CMakeLists.txt").rewrite {
                it.lines().toMutableList().apply {
                    val index = indexOfFirst { it.startsWith("project") }
                    add(index + 1, "set(CMAKE_C_FLAGS_RELEASE \"\${CMAKE_C_FLAGS_RELEASE} -g -Oz\")")
                    add(index + 2, "set(CMAKE_CXX_FLAGS_RELEASE \"\${CMAKE_CXX_FLAGS_RELEASE} -g -Oz\")")
                }.joinToString("\n")
            }
        }

        // 修改 so 模块下的oh-package.json5
        File(moduleFile, "oh-package.json5").rewrite {
            it.replace("lib$ORIGIN_MODULE_NAME", "lib$soName")
                .replace("\"$ORIGIN_MODULE_NAME\"", "\"$harName\"")
                .replace("1.0.0", harVersion)
        }
        // 修改 so 模块下的Index.ets
        File(moduleFile, "Index.ets").rewrite {
            it.replace("lib$ORIGIN_MODULE_NAME.so", "lib$soName.so")
        }

        // 修改 so 模块下的module.json5
        File(moduleFile, "src/main/module.json5").rewrite {
            it.replace(ORIGIN_MODULE_NAME, soName)
        }

        // 将生成的 so copy 到arm64-v8a 目录下
        val soFile = File(project.buildDir, getLinkOutputDir()).listFiles().first { it.name.endsWith(".so") }
        val targetSoFile = createNewFile(moduleFile, "libs/arm64-v8a/${soFile.name}")
        soFile.copyTo(targetSoFile, true)

        // 将生成的 .h文件 copy 到 include 目录下
        val hFile = File(project.buildDir, getLinkOutputDir()).listFiles().first { it.name.endsWith(".h") }
        val targetHFile = createNewFile(moduleFile, "src/main/cpp/include/${hFile.name}")
        hFile.copyTo(targetHFile, true)

        // 修改 CMakeLists.txt
        File(moduleFile, "src/main/cpp/CMakeLists.txt").rewrite {
            val temp = "!@#$%^&*ewquihjkxxx)(*&^"//若“soFile.name”内包含kmp关键字则会出现问题
            it.replace("lib$ORIGIN_MODULE_NAME.so", temp)
                .replace("kmp", soName)
                .replace(temp, soFile.name) + "\n" + extension.linkSoList.map {
                    "target_link_libraries($soName PUBLIC $it)"
                }.joinToString("\n")
        }

        // 修改 napi_init.cpp
        File(moduleFile, "src/main/cpp/napi_init.cpp").rewrite {
            // 引入头文件
            var result = "#include \"${hFile.name}\"\n" + it
            val symbols = parseSymbols(hFile.readText())
            // 替换 getsymbol 方法
            result.replace("libkmp_symbols()", symbols.second)
                .replace("libkmp.so", "lib$soName.so")
                .replace("dynamic_ExportedSymbols*", symbols.first)
                .replace("\"$ORIGIN_MODULE_NAME\"", "\"$soName\"")
                .replace(ORIGIN_HAR_NAME, harName)
                .replace("\"/entry\"", "\"/" + rootModuleName + "\"")
        }

        // 修改 types 目录下的模块名称
        val typesModule = renameDirectory(File(moduleFile, "src/main/cpp/types/lib$ORIGIN_MODULE_NAME"), "lib$soName")

        // 修改 types 下的oh-package.json5
        File(typesModule, "oh-package.json5").rewrite {
            it.replace(ORIGIN_MODULE_NAME, soName)
        }

        // 生成.d.ts
        TypeDefineGenerator(project, typesModule, File(moduleFile, "Index.ets"), soName, extension.enableCompose).generate()
    }

    /**
     * 解析头文件，获取getSymbol 方法
     */
    private fun parseSymbols(content: String): Pair<String, String> {
        val split = content.split("\n").last {
            it.startsWith("extern ")
        }.split(" ")
        return split[1] to split[2].replace("void", "").removeSuffix(";")
    }
}
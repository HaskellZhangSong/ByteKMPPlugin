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

package com.bytedance.kmp.compose.ohos

import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ComposeInitCodeGenerator{

    private const val GENERATE_PACKAGE = "compose_init"
    private const val INIT_FILE_NAME = "ComposeInitCode"
    private const val LOCAL_FILE_NAME = "ohos_compose_meta_info"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun generateComposeInitCode(environment: SymbolProcessorEnvironment, resolver: Resolver) {
        val metaInfos = KspMetaInfoManager.getAllMetaInfo<ExportComposableMetaInfo>(resolver, ComposeExportGenerator.META_INFO_PACKAGE, ComposeExportGenerator.KSP_MEAT_INFO_KEY)
        val file = resolver.getAllFiles().firstOrNull {
            it.fileName == "$INIT_FILE_NAME.kt"
        }?.let {
            File(it.filePath).outputStream()
        } ?: environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, GENERATE_PACKAGE, INIT_FILE_NAME)
        file.writer().use {
            it.write("""
                    @file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "FunctionName")
                    @file:OptIn(ExperimentalForeignApi::class)
                    
                    package $GENERATE_PACKAGE

                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.remember
                    import androidx.compose.ui.window.ComposeController
                    import androidx.compose.ui.window.ContentData
                    import androidx.compose.ui.window.DefaultTransfer
                    import com.bytedance.kmp.ohos_ffi.annotation.ArkSoInitFunction
                    import kotlinx.cinterop.ExperimentalForeignApi
                    import platform.ohos.napi.napi_env
                    import platform.ohos.napi.napi_value
                    
                    @ArkSoInitFunction
                    @OptIn(ExperimentalForeignApi::class)
                    fun init(env: napi_env, exports: napi_value) {
                        println("ComposeInit: execute regNApi init")
                        // compose UI 注册
                        composeUIRegistry()
                    }
                    
                    internal fun composeUIRegistry() {
                        ${metaInfos.map { getRegisterCode(it, "                        ") }.joinToString("\n                        ")}
                    }

                """.trimIndent())
        }
        // 保存到本地，用于 har 生成 ets
        saveMetaInfoToLocal(metaInfos, resolver, environment)
    }

    private fun getRegisterCode(metaInfo: ExportComposableMetaInfo, space: String): String {
        val result = """
            ComposeController["${metaInfo.id}"] = ContentData(
                ${if (metaInfo.paramTransformFunction == null) "DefaultTransfer" else "{ ${metaInfo.paramTransformFunction.definePackage}.${metaInfo.paramTransformFunction.defineMethodName}(it) }"}, 
                ${metaInfo.touchInterceptor.ifEmpty { "null" }},
                ${metaInfo.needBackground},
                ${metaInfo.disableRecycleNode},
                ${metaInfo.withOffscreenRender}
            ) @Composable {
                ${metaInfo.wrapFunction.definePackage}.${metaInfo.wrapFunction.defineMethodName}()
            }
        """.trimIndent()
        return result.split("\n").joinToString("\n$space")
    }

    private fun saveMetaInfoToLocal(metaInfos: List<ExportComposableMetaInfo>, resolver: Resolver, environment: SymbolProcessorEnvironment) {

        if (metaInfos.isEmpty()) {
            return
        }
        val file = resolver.getAllFiles().firstOrNull {
            it.fileName == "${LOCAL_FILE_NAME}.kt"
        }?.let {
            File(it.filePath).outputStream()
        } ?: environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, "", LOCAL_FILE_NAME)
        file.writer().use {
            val jsonStr = json.encodeToString(metaInfos)
            // 因为生成的是 json 而不是 kotlin 代码会在编译阶段失败，所以整体加上注解绕过编译
            it.write("/*\n${jsonStr}\n*/")
        }
    }

}
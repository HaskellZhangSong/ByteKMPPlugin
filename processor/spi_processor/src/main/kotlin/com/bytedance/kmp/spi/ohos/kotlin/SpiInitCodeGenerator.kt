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

package com.bytedance.kmp.spi.ohos.kotlin

import com.bytedance.kmp.spi.common.SPIMetaInfoGenerator.Companion.SPI_IMPL_META_INFO_GENERATE_PACKAGE
import com.bytedance.kmp.spi.common.SPIMetaInfoGenerator.Companion.SPI_IMPL_META_INFO_KEY
import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import kotlinx.serialization.Serializable
import java.io.File

class SpiInitCodeGenerator(val clazz: KSClassDeclaration, val environment: SymbolProcessorEnvironment, val resolver: Resolver){

    companion object {

        const val SPI_INIT_FILE_NAME = "SPI_SO_INIT"

        // 为顶层模块生成 spi 初始化代码
        fun generateSpiInitCode(environment: SymbolProcessorEnvironment, resolver: Resolver) {
            val metaInfos = KspMetaInfoManager.getAllMetaInfo<SpiImplMetaInfo>(resolver, SPI_IMPL_META_INFO_GENERATE_PACKAGE, SPI_IMPL_META_INFO_KEY)
            val file = resolver.getAllFiles().firstOrNull {
                it.fileName == "$SPI_INIT_FILE_NAME.kt"
            }?.let {
                File(it.filePath).outputStream()
            } ?: environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, SPI_IMPL_META_INFO_GENERATE_PACKAGE, SPI_INIT_FILE_NAME)
            file.writer().use {
                it.write("""
                    @file:OptIn(ExperimentalForeignApi::class)
                    
                    package $SPI_IMPL_META_INFO_GENERATE_PACKAGE

                    import com.bytedance.kmp.ohos_ffi.annotation.ArkSoInitFunction
                    import com.bytedance.kmp.spi.KmpServiceManager
                    import kotlinx.cinterop.ExperimentalForeignApi
                    import platform.ohos.napi.*
                    
                    private var hasInit = false
                     
                    @ArkSoInitFunction
                    fun initSpi(env: napi_env, exports: napi_value) {
                        if (hasInit) {
                            return
                        }
                        ${metaInfos.map { "KmpServiceManager.put(${it.service}::class, ${it.impl}${if (it.isObject) "" else "()"})" }.joinToString("\n                        ")}
                        hasInit = true
                    }
                """.trimIndent())
            }
        }
    }
}

@Serializable
data class SpiImplMetaInfo(val service: String, val impl: String, val isObject: Boolean = false)
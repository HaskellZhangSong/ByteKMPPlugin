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

package com.bytedance.kmp.spi.common

import com.bytedance.kmp.spi.ohos.kotlin.SpiImplMetaInfo
import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import java.io.File

class SpiInitCodeGenerator(val environment: SymbolProcessorEnvironment) {

    companion object {
        private const val INIT_CODE_PACKAGE = "com.bytedance.kmp.spi.generated"
        private const val INIT_CODE_FUNCTION_NAME = "initKmpSpi"
    }

    /**
     * 收集项目中所有的 spi 信息(当前模块 + 子模块 metadata)，生成 SPI 初始化代码
     */
    fun generateInitCode(resolver: Resolver) {
        val spiImplList = mutableListOf<SpiImplMetaInfo>().apply {
            // 子模块
            addAll(
                KspMetaInfoManager.getAllMetaInfo<SpiImplMetaInfo>(resolver,
                    SPIMetaInfoGenerator.Companion.SPI_IMPL_META_INFO_GENERATE_PACKAGE,
                    SPIMetaInfoGenerator.Companion.SPI_IMPL_META_INFO_KEY
                ))
            // 当前模块
            addAll(SPIMetaInfoGenerator.generateMetaInfo(environment, resolver, true))
        }
        var file = resolver.getAllFiles().firstOrNull {
            it.fileName == "$INIT_CODE_FUNCTION_NAME.kt"
        }
        if (file != null && spiImplList.isEmpty()) {
            // 避免空列表覆盖已经写入的初始化代码
            return
        }
        val outputStream = file?.let {
            File(it.filePath).outputStream()
        } ?: environment.codeGenerator.createNewFile(Dependencies.Companion.ALL_FILES, INIT_CODE_PACKAGE, INIT_CODE_FUNCTION_NAME)
        outputStream.writer().use {
            it.write(
                """
                    package $INIT_CODE_PACKAGE
                    
                    import com.bytedance.kmp.spi.IKmpServiceManagerHost
                    import com.bytedance.kmp.spi.KmpServiceManager
                    
                    fun $INIT_CODE_FUNCTION_NAME(host: IKmpServiceManagerHost? = null) {
                        if (host == null) {
                            KmpServiceManager.init()
                        } else {
                            KmpServiceManager.init(host)
                        }
                        ${spiImplList.map { "KmpServiceManager.put(${it.service}::class, ${it.impl}${if (it.isObject) "" else "()"})" }.joinToString("\n                        ")}
                    }
                    
                """.trimIndent()
            )
        }
    }
}
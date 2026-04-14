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

package com.bytedance.kmp.spi.ohos

import com.bytedance.kmp.ohos_ffi.generator.OhosFfiProcessor
import com.bytedance.kmp.spi.common.SPIMetaInfoGenerator
import com.bytedance.kmp.spi.ohos.arkts.ArkTsSpiClassGenerator
import com.bytedance.kmp.spi.ohos.kotlin.SpiInitCodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated

class KmpSpiOhosProcessor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    // 是否用于产出 so 的顶层模块，用于判断是否生成 ffi 初始化代码
    private val isRootModule = environment.options[OhosFfiProcessor.KEY_ROOT_MODULE]?.toBoolean() ?: false
    private var isFirstRound = true
    private var preRoundHasGenerateFiles = false
    private var preRoundHasNewSpiImpl = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        println("KSP Processor: KmpSpiProcessor process")
        handleArkTsImpl(resolver)
        handleSpiInitCode(resolver)
        isFirstRound = false
        return emptyList()
    }

    private fun handleArkTsImpl(resolver: Resolver) {
        // 生成 spi impl 类
        val result = ArkTsSpiClassGenerator.generate(environment, resolver)
        println("KSP Processor: KmpSpiProcessor generatedFile ${environment.codeGenerator.generatedFile.map { it.name }.toList()}")
        preRoundHasGenerateFiles = result.size > 0
    }

    private fun handleSpiInitCode(resolver: Resolver) {
        val size = SPIMetaInfoGenerator.generateMetaInfo(environment, resolver).size
        if (isRootModule && (isFirstRound || preRoundHasNewSpiImpl)) {
            SpiInitCodeGenerator.generateSpiInitCode(environment, resolver)
        }
        preRoundHasNewSpiImpl = size > 0
    }
}
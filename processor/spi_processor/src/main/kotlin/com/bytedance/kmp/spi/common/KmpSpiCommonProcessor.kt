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

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated

class KmpSpiCommonProcessor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    companion object {
        const val KEY_ROOT_MODULE = "kmp_root_module"
    }

    // 是否用于产出 so 的顶层模块，用于判断是否生成 ffi 初始化代码
    private val isRootModule = environment.options[KEY_ROOT_MODULE]?.toBoolean() ?: false
    // 是否在当前模块自定义生成 spi impl，是的话则在顶层模块不再处理
    private val initCodeGenerator = SpiInitCodeGenerator(environment)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        environment.logger.warn("[KmpSPI] KmpSpiProcessor process common")
        if (!isRootModule) {
            // 子模块生成 metadata 代码
            environment.logger.warn("[KmpSPI] SPIMetaInfoGenerator.generateMetaInfo")
            SPIMetaInfoGenerator.generateMetaInfo(environment, resolver)
        } else {
            // 顶层模块生成 spi 初始化代码
            initCodeGenerator.generateInitCode(resolver)
        }
        return emptyList()
    }
}
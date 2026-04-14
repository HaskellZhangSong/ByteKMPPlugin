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

import com.bytedance.kmp.ohos_ffi.generator.OhosFfiProcessor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated

class ComposeProcessor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    private val isRootModule = environment.options[OhosFfiProcessor.KEY_ROOT_MODULE]?.toBoolean() ?: false
    private var isFirstRound = true
    private var preRoundHasNewComposable = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val generated = handleExportComposable(resolver)
        handleComposeInitCode(resolver)
        isFirstRound = false
        preRoundHasNewComposable = generated
        return emptyList()
    }

    private fun handleExportComposable(resolver: Resolver): Boolean {
        return ComposeExportGenerator.generate(environment, resolver).isNotEmpty()
    }

    private fun handleComposeInitCode(resolver: Resolver) {
        if (isRootModule && (isFirstRound || preRoundHasNewComposable)) {
            ComposeInitCodeGenerator.generateComposeInitCode(environment, resolver)
        }
    }
}
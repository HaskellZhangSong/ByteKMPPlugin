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

@file:OptIn(KspExperimental::class)

package com.bytedance.kmp.ohos_ffi.generator

import com.bytedance.kmp.ohos_ffi.generator.js_bind.BaseJsBindGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.ClassMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.CustomSoInitFuncMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.CustomSoInitGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.FunctionMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindClassGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindFunctionGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindPropertyGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.PropertyMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindEnumGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindInterfaceGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.customtransform.CustomTransformManager
import com.bytedance.kmp.ohos_ffi.generator.so_init.SoInitGenerator
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated

class OhosFfiProcessor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    companion object {
        const val KEY_ROOT_MODULE = "ohos_ffi_root_module"
        const val KEY_MODULE_PACKAGE = "ohos_ffi_module_package"
    }

    // 是否用于产出 so 的顶层模块，用于判断是否生成 ffi 初始化代码
    private val isRootModule = environment.options[KEY_ROOT_MODULE]?.toBoolean() ?: false

    // 当前模块包名，用于生成 Meta 代码
    private lateinit var modulePackageName: String

    private var isFirstRound = true
    private var preRoundHasGenerateFiles = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        CustomTransformManager.init(resolver)
        println("KSP Processor: OhosFfiProcessor process")
        modulePackageName = environment.options[KEY_MODULE_PACKAGE] ?: resolver.getModuleName().asString()
        // 为当前模块生成 js binding 代码
        val size = generateJsBinding(resolver)
        // 为 Root Module 生成 ffi 整体的初始化代码
        println("KSP Processor: OhosFfiProcessor generatedFile ${environment.codeGenerator.generatedFile.map { it.name }.toList()}")
//        MetaInfoGenerator(environment, metaInfo).generate(modulePackageName, resolver)
        if (isRootModule && (isFirstRound || preRoundHasGenerateFiles)) {
            // 上一轮没有代码生成，走最终的 SoInit 方法生成
            SoInitGenerator(environment, resolver).process()
        }
        preRoundHasGenerateFiles = size > 0
        isFirstRound = false
        return emptyList()
    }

    private fun generateJsBinding(resolver: Resolver): Int {
        var size = 0
        // 生成 Property Binding
        size += JsBindPropertyGenerator.generate(environment, resolver)
        // 生成 Function Binding
        size += JsBindFunctionGenerator.generate(environment, resolver)
        // 生成 Class Binding
        size += JsBindClassGenerator.generate(environment, resolver)
        // 生成 Enum Binding
        size += JsBindEnumGenerator.generate(environment, resolver)
        // 生成业务自定义 So Init 方法MetaInfo
        size += CustomSoInitGenerator.generate(environment, resolver)
        // ArkTs 实现的接口类型
        size += JsBindInterfaceGenerator.generate(environment, resolver)
        return size
    }

    override fun finish() {
        super.finish()
        CustomTransformManager.clear()
    }
}
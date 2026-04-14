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

package com.bytedance.kmp.ohos_ffi.generator.js_bind.export

import com.bytedance.kmp.ohos_ffi.generator.js_bind.BaseJsBindGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.CustomSoInitFuncMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.type.checkPublic
import com.bytedance.kmp.ohos_ffi.generator.type.checkTopLevel
import com.bytedance.kmp.ohos_ffi.generator.type.getSimpleName
import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

class CustomSoInitGenerator(val function: KSFunctionDeclaration, environment: SymbolProcessorEnvironment, resolver: Resolver) : BaseJsBindGenerator<CustomSoInitFuncMetaInfo>(environment, resolver) {

    companion object {

        const val KSP_MEAT_INFO_KEY = "ksp_meta_info_key_custom_so_init_func"

        fun generate(environment: SymbolProcessorEnvironment, resolver: Resolver): Int {
            return resolver.getSymbolsWithAnnotation(ANNOTATION_SO_INIT_FUNC)
                .filterIsInstance<KSFunctionDeclaration>()
                .apply {
                    forEach { func ->
                        CustomSoInitGenerator(func, environment, resolver).let {
                            it.generate()
                            val metaInfo = it.createMetaInfo()
                            KspMetaInfoManager.saveMetaInfo(environment, META_INFO_PACKAGE, "custom_so_init_${func.qualifiedName!!.asString().replace(".", "_")}", KSP_MEAT_INFO_KEY, metaInfo)
                        }
                    }
                }.toList().size
        }
    }

    override fun check() {}

    override fun generate() {
        checkPublic(function)
        checkTopLevel(function)
        checkPackage(function)
        if (function.parameters.size != 2
            || getSimpleName(function.returnType?.resolve()) != "Unit"
            || getSimpleName(function.parameters[0].type.resolve()) != "napi_env"
            || getSimpleName(function.parameters[1].type.resolve()) != "napi_value"
        ) {
            throw RuntimeException("${getOutputName(function)} is not a valid so init function")
        }
    }

    override fun createMetaInfo(): CustomSoInitFuncMetaInfo {
        // 仅用于注入到 init 方法中，不对 TS 导出
        return CustomSoInitFuncMetaInfo(function.packageName.asString(), JsBindFunctionGenerator.generateFunctionReference(function), getPriority())
    }

    private fun getPriority(): Int {
        val anno = function.annotations.firstOrNull {
            it.annotationType.resolve().declaration
                .qualifiedName?.asString() == ANNOTATION_SO_INIT_FUNC
        } ?: return Int.MAX_VALUE
        return anno.arguments.firstOrNull {
            it.name?.asString() == "priority"
        }?.value as? Int ?: Int.MAX_VALUE
    }
}
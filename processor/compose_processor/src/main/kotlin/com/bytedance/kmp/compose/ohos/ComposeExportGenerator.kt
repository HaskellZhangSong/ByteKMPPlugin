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

import com.bytedance.kmp.ohos_ffi.generator.js_bind.BaseJsBindGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.DefineMethodInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindFunctionGenerator
import com.bytedance.kmp.ohos_ffi.generator.type.TypeEnum
import com.bytedance.kmp.ohos_ffi.generator.type.TypeManager
import com.bytedance.kmp.ohos_ffi.generator.type.checkTopLevel
import com.bytedance.kmp.ohos_ffi.generator.type.getAnnotation
import com.bytedance.kmp.ohos_ffi.generator.type.nullable
import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import kotlinx.serialization.Serializable

class ComposeExportGenerator(
    val function: KSFunctionDeclaration,
    environment: SymbolProcessorEnvironment,
    resolver: Resolver
) : BaseJsBindGenerator<ExportComposableMetaInfo>(environment, resolver) {

    companion object {

        private const val ANNOTATION_COMPOSE_EXPORT = "com.bytedance.kmp.compose.ohos.ArkTsExportComposable"
        private const val ANNOTATION_COMPOSABLE = "androidx.compose.runtime.Composable"
        const val META_INFO_PACKAGE = "compose_export_meta_info"
        const val KSP_MEAT_INFO_KEY = "ksp_meta_info_key_compose_export"
        fun generate(environment: SymbolProcessorEnvironment, resolver: Resolver): List<Any> {
            return resolver.getSymbolsWithAnnotation(ANNOTATION_COMPOSE_EXPORT)
                .filterIsInstance<KSFunctionDeclaration>()
                .map { func ->
                    ComposeExportGenerator(func, environment, resolver).let {
                        it.check()
                        it.generate()
                        KspMetaInfoManager.saveMetaInfo(environment, META_INFO_PACKAGE, "export_compose_func_${func.qualifiedName!!.asString().replace(".", "_")}",
                            KSP_MEAT_INFO_KEY, it.createMetaInfo())
                    }
                }.toList()
        }
    }

    private val packageName = function.packageName.asString()
    private val generatePackageName = "$packageName.compose_export_wrap"
    private val generateFileName = "${function.qualifiedName!!.asString().replace(".", "_")}"
    private val wrapFuncName = "wrapFunctionFor${function.simpleName!!.asString()}"
    private val paramTransformFuncName = "paramTransformFunctionFor${function.simpleName!!.asString()}"


    override fun check() {
        checkTopLevel(function)
        if (function.getAnnotation(ANNOTATION_COMPOSABLE) == null) {
            throw RuntimeException("@ArkTsExportComposable must be added to a @Composable function")
        }
        if (function.parameters.size > 1) {
            throw RuntimeException("only 0 or 1 param is allowed for @ArkTsExportComposable function ${getOutputName(function)}")
        }
        function.parameters.forEach {
            val type = it.type.resolve()
            if (!TypeManager.isSupportClass(type)) {
                throw RuntimeException("param type of @ArkTsExportComposable function ${getOutputName(function)} is not support")
            }
        }
        val returnType = function.returnType?.resolve()?.declaration?.simpleName?.asString()
        if (returnType != null && returnType != "Unit") {
            throw RuntimeException("return type of @ArkTsExportComposable function ${getOutputName(function)} must be Unit")
        }

        checkPackage(function)
    }

    override fun generate() {
        val types = function.parameters.map { it.type }
        val file =
            environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, generatePackageName, generateFileName)
        file.writer().use {
            it.write(
                importCode(packageName, generatePackageName, types.map { it.resolve() }.toList())
                        + composeImportCode()
                        + headerCommentCode()
                        + wrapFunctions()
                        + paramTransformFunction()
            )
        }
    }

    private fun composeImportCode(): String {
        return """
            import androidx.compose.runtime.*
            import androidx.compose.ui.window.currentRenderNodeParams
            import com.bytedance.kmp.compose.ohos.library.withTextToolbar
            
        """.trimIndent()
    }

    private fun wrapFunctions(): String {
        if (function.parameters.isEmpty()) {
            return """
                @Composable
                internal fun $wrapFuncName(napiValue: napi_value? = null) {
                    withTextToolbar {
                        ${JsBindFunctionGenerator.generateFunctionReference(function)}()
                    }
                }
                
            """.trimIndent()
        }
        val paramType = function.parameters.first().type.resolve()
        return """
            @Composable
            internal fun $wrapFuncName() {
                val param = currentRenderNodeParams<${paramType.declaration.simpleName.asString()}>()
                withTextToolbar {
                    ${JsBindFunctionGenerator.generateFunctionReference(function)}(param)
                }
            }
            
        """.trimIndent()
    }

    private fun paramTransformFunction(): String {
        val paramType = function.parameters.firstOrNull()?.type?.resolve() ?: return ""
        return """
                fun $paramTransformFuncName(napiValue: napi_value? = null): ${paramType.declaration.simpleName.asString()} {
                    return ${TypeManager.jsObj2KotlinObjCode("napiValue?", paramType, paramType.nullable())}
                }
            """.trimIndent()
    }

    private fun getId(): String {
        val id = function.getAnnotation(ANNOTATION_COMPOSE_EXPORT)!!.arguments.firstOrNull {
            it.name?.asString() == "id"
        }?.value as? String ?: "$packageName.${function.simpleName!!.asString()}"
        return id.replace(".", "_")
    }

    private fun getHitTestMode(): Int {
        val value = function.getAnnotation(ANNOTATION_COMPOSE_EXPORT)!!.arguments.firstOrNull {
            it.name?.asString() == "hitTestMode"
        }?.value as? KSType ?: return 0
        return when (value.declaration.simpleName.asString()) {
            "ARKUI_HIT_TEST_MODE_DEFAULT" -> 0
            "ARKUI_HIT_TEST_MODE_BLOCK" -> 1
            "ARKUI_HIT_TEST_MODE_TRANSPARENT" -> 2
            "ARKUI_HIT_TEST_MODE_NONE" -> 3
            else -> 0
        }
    }

    private fun getTouchInterceptor(): String {
        return (function.getAnnotation(ANNOTATION_COMPOSE_EXPORT)!!.arguments.firstOrNull {
            it.name?.asString() == "touchInterceptor"
        }?.value as? String)?.trim() ?: ""
    }

    private fun getWithInterop(): Boolean {
        return function.getAnnotation(ANNOTATION_COMPOSE_EXPORT)!!.arguments.firstOrNull {
            it.name?.asString() == "withInterop"
        }?.value as? Boolean ?: return false
    }

    private fun getNeedBackground(): Boolean {
        return function.getAnnotation(ANNOTATION_COMPOSE_EXPORT)!!.arguments.firstOrNull {
            it.name?.asString() == "needBackground"
        }?.value as? Boolean ?: return true
    }

    private fun getDisableRecycleNode(): Boolean {
        return function.getAnnotation(ANNOTATION_COMPOSE_EXPORT)!!.arguments.firstOrNull {
            it.name?.asString() == "disableRecycleNode"
        }?.value as? Boolean ?: return false
    }

    private fun getWithOffscreenRender(default: Boolean): Boolean {
        return function.getAnnotation(ANNOTATION_COMPOSE_EXPORT)!!.arguments.firstOrNull {
            it.name?.asString() == "withOffscreenRender"
        }?.value as? Boolean ?: return default
    }

    override fun createMetaInfo(): ExportComposableMetaInfo {
        val withInterop = getWithInterop()
        if (function.parameters.size == 0) {
            return ExportComposableMetaInfo(
                getId(),
                DefineMethodInfo(generatePackageName, wrapFuncName),
                null,
                "",
                false,
                getHitTestMode(),
                getTouchInterceptor(),
                withInterop,
                getNeedBackground(),
                getDisableRecycleNode(),
                getWithOffscreenRender(withInterop),
            )
        } else {
            val paramType = function.parameters.first().type.resolve()
            return ExportComposableMetaInfo(
                getId(),
                DefineMethodInfo(generatePackageName, wrapFuncName),
                DefineMethodInfo(generatePackageName, paramTransformFuncName),
                TypeManager.jsTypeStr(paramType),
                TypeManager.matchType(paramType, TypeEnum.CUSTOM_CLASS),
                getHitTestMode(),
                getTouchInterceptor(),
                withInterop,
                getNeedBackground(),
                getDisableRecycleNode(),
                getWithOffscreenRender(withInterop),
            )
        }
    }

}

@Serializable
data class ExportComposableMetaInfo(val id: String,
                                    val wrapFunction: DefineMethodInfo,
                                    val paramTransformFunction: DefineMethodInfo? = null,
                                    val jsParamType: String = "",
                                    val isCustomType: Boolean = false,
                                    val hitTestMode: Int = 0,
                                    val touchInterceptor: String = "",
                                    val withInterop: Boolean = false,
                                    val needBackground: Boolean = true,
                                    val disableRecycleNode: Boolean = false,
                                    val withOffscreenRender: Boolean = false,
    )
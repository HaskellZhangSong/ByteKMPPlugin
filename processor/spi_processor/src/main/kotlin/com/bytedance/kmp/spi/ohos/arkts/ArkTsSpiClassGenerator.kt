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

package com.bytedance.kmp.spi.ohos.arkts

import com.bytedance.kmp.ohos_ffi.generator.js_bind.BaseJsBindGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.FunctionMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindClassGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindFunctionGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindInterfaceGenerator
import com.bytedance.kmp.ohos_ffi.generator.type.TypeManager
import com.bytedance.kmp.ohos_ffi.generator.type.checkPublic
import com.bytedance.kmp.ohos_ffi.generator.type.isSuspend
import com.bytedance.kmp.spi.common.SPIMetaInfoGenerator.Companion.ANNOTATION_SPI_IMPL
import com.bytedance.kmp.spi.ohos.kotlin.SpiInitCodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * @ArkTsSpiClass
 * ArkTs实现，kotlin使用napi调用
 * 这个类用于生成ohosArm64Main下的kotlin调用代码
 */
class ArkTsSpiClassGenerator(
    val clazz: KSClassDeclaration,
    environment: SymbolProcessorEnvironment,
    resolver: Resolver
) : BaseJsBindGenerator<Any>(environment, resolver) {

    companion object {
        const val ARK_TS_SPI_CLASS_PACKAGE_SUFFIX = "arkts_spi_class"
        const val ANNOTATION_ARKTS_SPI = "com.bytedance.kmp.spi.annotation.ArkTsSpiClass"
        const val ARK_TS_SPI_CLASS_GENERATE_PACKAGE = "call_arkts_from_kotlin"
        fun generate(environment: SymbolProcessorEnvironment, resolver: Resolver): List<Any> {
            return resolver.getSymbolsWithAnnotation(ANNOTATION_ARKTS_SPI)
                .filterIsInstance<KSClassDeclaration>()
                .map {
                    ArkTsSpiClassGenerator(it, environment, resolver).let {
                        it.check()
                        it.generate()
                        it.createMetaInfo()
                    }
                }.toList()
        }
    }

    private val packageName = clazz.packageName.asString()
    private val className = clazz.simpleName.asString()
    private val arkModelName =
        clazz.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_ARKTS_SPI
        }?.arguments?.firstOrNull { it.name?.asString() == "arkModule" }?.value.toString()

    private val generatePackageName = "$packageName.$ARK_TS_SPI_CLASS_PACKAGE_SUFFIX"
    private val implClassName = "NapiImpl_$className"
    private val generateFileName = "${clazz.packageName.asString().replace(".", "_")}_${clazz.simpleName.asString()}"

    private val allSpiFunctions = clazz.getAllFunctions()
        .filter { !isObjHashcodeFun(it) && !isObjToStringFun(it) && !isObjEqualsFun(it) }

    override fun check() {
        if (clazz.parent !is KSFile) {
            throw RuntimeException("@ArkTsSpiClass must be added to a TopLevel Class, ${getOutputName(clazz)}")
        }
        if (!isArkTsInterfaceType(clazz)) {
            throw RuntimeException("@ArkTsSpiClass must used with @KotlinExportInterface, ${getOutputName(clazz)}")
        }
        if (arkModelName.isNullOrEmpty()) {
            throw RuntimeException("arkModelName must be set")
        }
        checkPackage(clazz)
    }

    override fun generate() {
        val types = allSpiFunctions.map { it.parameters }.flatten().map { it.type } + allSpiFunctions.map { it.returnType!! }
        val file =
            environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, generatePackageName, generateFileName)
        file.writer().use {
            it.write(
                importCode(packageName, generatePackageName, types.map { it.resolve() }.toList())
                        + headerCommentCode()
                        + generateClassTitle()
                        + allSpiFunctions.map { generateFunCall(it) }.joinToString("\n")
                        + "\n}"
            )
        }
        println("KSP Processor: KmpSpiProcessor generate new file ${generateFileName}")
    }

    private fun generateClassTitle(): String {
        return """
            import ${JsBindInterfaceGenerator.getGeneratedPackage(clazz.packageName.asString())}.*
            
            @${ANNOTATION_SPI_IMPL}($className::class)
            class $implClassName : $className {
                val instance by lazy {
                    ArkModule("$arkModelName").getExportInstance("instanceOf${className}")!!.napiValue!!.${JsBindClassGenerator.getKotlinInstanceMethodName(className)}()!!
                }
        """.trimIndent() + "\n\n"
    }

    private fun generateFunCall(function: KSFunctionDeclaration): String {
        val name = function.simpleName.asString()
        val kotlinParams = function.parameters.map { "${it.name!!.asString()}: ${TypeManager.kotlinTypeStr(it.type.resolve())}" }.joinToString(", ")
        val isThreadSafe = function.annotations.firstOrNull {anno ->
            anno.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_ARK_THREAD_SAFE
        } != null
        return if (isSuspend(function)) {
            """
                override suspend fun $name($kotlinParams): ${TypeManager.kotlinTypeStr(function.returnType!!.resolve())} {
                    return withContext(Dispatchers.Main) {
                        instance.$name(${function.parameters.map { it.name!!.asString() }.joinToString(", ")})
                    }
                }
            """.trimIndent()
        } else if (isThreadSafe) {
            """
                override fun $name($kotlinParams): ${TypeManager.kotlinTypeStr(function.returnType!!.resolve())} {
                    return runBlocking(Dispatchers.Main.immediate) {
                        instance.$name(${function.parameters.map { it.name!!.asString() }.joinToString(", ")})
                    }
                }
            """.trimIndent()
        } else {
            """
                override fun $name($kotlinParams): ${TypeManager.kotlinTypeStr(function.returnType!!.resolve())} {
                    return instance.$name(${function.parameters.map { it.name!!.asString() }.joinToString(", ")})
                }
            """.trimIndent()
        }
    }

    override fun createMetaInfo(): Any {
        return Any()
    }

}
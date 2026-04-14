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
import com.bytedance.kmp.ohos_ffi.generator.js_bind.ClassMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.DefineMethodInfo
import com.bytedance.kmp.ohos_ffi.generator.type.TypeManager
import com.bytedance.kmp.ohos_ffi.generator.type.isSuspend
import com.bytedance.kmp.ohos_ffi.generator.type.nullable
import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

class JsBindInterfaceGenerator(val clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment, resolver: Resolver): BaseJsBindGenerator<ClassMetaInfo>(environment, resolver) {

    companion object {

        const val PACKAGE_SUFFIX = "js_bind_import_interface"
        const val KSP_MEAT_INFO_KEY = "ksp_meta_info_key_js_bind_import_interface"

        fun generate(environment: SymbolProcessorEnvironment, resolver: Resolver): Int {
            return resolver.getSymbolsWithAnnotation(ANNOTATION_ARK_IMPORT_INTERFACE)
                .filterIsInstance<KSClassDeclaration>()
                .distinct()
                .apply {
                    forEach { clazz ->
                        JsBindInterfaceGenerator(clazz, environment, resolver).let {
                            it.check()
                            it.generate()
                            val metaInfo = it.createMetaInfo()
                            KspMetaInfoManager.saveMetaInfo(environment, META_INFO_PACKAGE, "js_bind_import_interface_${clazz.qualifiedName!!.asString().replace(".", "_")}",
                                KSP_MEAT_INFO_KEY, metaInfo)
                        }
                    }
                }.toList().size
        }

        fun getGeneratedPackage(originPackage: String): String {
            return "$originPackage.$PACKAGE_SUFFIX"
        }
    }

    private val packageName = clazz.packageName.asString()
    private val className = clazz.simpleName.asString()
    private val generateName = getCustomName(clazz) ?: clazz.simpleName.asString()
    private val generatePackageName = getGeneratedPackage(packageName)
    private val defineMethodName = "defineClassFor${generateName}"
    private val generateFileName = "JsImportInterfaceBinding_${className}"
    private val getKotlinInstanceMethodName = JsBindClassGenerator.getKotlinInstanceMethodName(className)
    private val safeSuspendWrapperClass = "SafeSuspendWrapperFor$className"

    private val exportFunctions = clazz.getAllFunctions().filter {
        !isObjHashcodeFun(it) && !isObjToStringFun(it) && !isObjEqualsFun(it)
    }

    override fun check() {
        environment.logger.warn("[JsBindInterfaceGenerator] start check Interface ${getOutputName(clazz)}")
        checkPackage(clazz)
        if (clazz.classKind != ClassKind.INTERFACE) {
            throw RuntimeException("@KotlinExportInterface must be added to an Interface, ${getOutputName(clazz)}")
        }
        // 检查类型
        exportFunctions.forEach {
            environment.logger.warn("[JsBindInterfaceGenerator] start check Function ${getOutputName(it)}")
            checkFunctionType(it, true)
        }
        val functionsName = exportFunctions.map { getCustomName(it) ?: it.simpleName.asString() }
        functionsName.forEach {
            if (functionsName.count { p -> p == it } > 1) {
                throw RuntimeException("more than one functions named '${it}'")
            }
        }
    }

    override fun generate() {
        val types = exportFunctions.map { it.parameters }.flatten().map { it.type } + exportFunctions.map { it.returnType!! }
        val file =
            environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, generatePackageName, generateFileName)
        file.writer().use {
            it.write(
                importCode(packageName, generatePackageName, types.map { it.resolve() }.toList())
                        + generateGetKotlinObjCode()
                        + headerCommentCode()
                        + generateClassTitle()
                        + exportFunctions.map { generateFunCall(it) }.joinToString("\n")
                        + "\n}"
                        + "\n${safeSuspendCode()}"
            )
        }
        println("KSP Processor: OhosFfiProcessor generate new file $generateFileName")
    }

    private fun generateGetKotlinObjCode(): String {
        return """
            fun napi_value.${getKotlinInstanceMethodName}(): ${className}? {
                if (isUndefined()) {
                    return null
                }
                val instance = ArkInstance(this)
                return $generateFileName(instance)
            }
            
            
        """.trimIndent()
    }

    private fun generateClassTitle(): String {
        return """
            class $generateFileName(val instance: ArkInstance) : $className {
                
        """.trimIndent() + "\n\n"
    }

    private fun generateFunCall(function: KSFunctionDeclaration): String {
        val isThreadSafe = function.annotations.firstOrNull {anno ->
            anno.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_ARK_THREAD_SAFE
        } != null
        val name = function.simpleName.asString()
        val kotlinParams = function.parameters.map {
            it.name!!.asString() + ":" + TypeManager.kotlinTypeStr(it.type.resolve())
        }.joinToString(",")
        val napiParams = if (function.parameters.isEmpty()) {
            ""
        } else {
            function.parameters.map {
                val name = it.name!!.asString()
                "if (${name} == null) getUndefined() else ${TypeManager.kotlinObj2JsObjCode(name, it.type.resolve(), true)} ?: getUndefined()"
            }.joinToString(", ")
        }
        val isReturnUnit = function.returnType?.resolve()?.toString() == "Unit"
        val returnTypeNullable = function.returnType?.resolve()?.nullable() == true
        val resultTypeTransform = if (isReturnUnit) {
            ""
        } else if (returnTypeNullable) {
            """
                if (napi_result.isUndefined()) {
                    return null
                }
                return ${TypeManager.jsObj2KotlinObjCode("napi_result!!", function.returnType!!.resolve(), function.returnType!!.resolve().nullable())}
            """.trimIndent()
        } else {
            "return ${TypeManager.jsObj2KotlinObjCode("napi_result!!", function.returnType!!.resolve(), function.returnType!!.resolve().nullable())}"
        }
        val suspendTypeTransform = if (isReturnUnit) {
            "con.resume(Unit)"
        } else if (returnTypeNullable) {
            """
                if (it.isUndefined()) {
                    con.resume(null)
                } else {
                    con.resume(${TypeManager.jsObj2KotlinObjCode("it", function.returnType!!.resolve(), function.returnType!!.resolve().nullable())})
                }
            """.trimIndent()
        } else {
            "con.resume(${TypeManager.jsObj2KotlinObjCode("it", function.returnType!!.resolve(), function.returnType!!.resolve().nullable())})"
        }
        val callSuperImpl = if (!function.isAbstract) {
            // 有默认实现，允许 ArkTs 不实现对应方法
            """
                if (function.isUndefined()) {
                    return super.$name(${function.parameters.map { it.name!!.asString() }.joinToString(", ")})
                }
            """.trimIndent()
        } else {
            ""
        }
        return if (!isSuspend(function)) {
            // suspend 方法默认线程安全，这里只对标注了@KotlinThreadSafe 的非 suspend 方法做处理
            if (isThreadSafe) {
                """
                    override fun $name($kotlinParams): ${TypeManager.kotlinTypeStr(function.returnType!!.resolve())} {
                        fun innterFunc(): ${TypeManager.kotlinTypeStr(function.returnType!!.resolve())} {
                            val function = instance.getFunction("$name")
                            ${callSuperImpl.lines().joinToString("\n                ")}
                            val params = listOf<napi_value>($napiParams).toTypedArray()
                            val napi_result = function.getNapiValue().call(instance.getNapiValue(), params)
                            ${resultTypeTransform.lines().joinToString("\n                    ")}
                        }
                    
                        return runBlocking(Dispatchers.Main.immediate) {
                            innterFunc()
                        }
                    }
                """.trimIndent()
            } else {
                """
                    override fun $name($kotlinParams): ${TypeManager.kotlinTypeStr(function.returnType!!.resolve())} {
                        val function = instance.getFunction("$name")
                        ${callSuperImpl.lines().joinToString("\n                ")}
                        val params = listOf<napi_value>($napiParams).toTypedArray()
                        val napi_result = function.getNapiValue().call(instance.getNapiValue(), params)
                        ${resultTypeTransform.lines().joinToString("\n                    ")}
                    }
                """.trimIndent()
            }
        } else {
            """
                override suspend fun $name($kotlinParams): ${TypeManager.kotlinTypeStr(function.returnType!!.resolve())} {
                    return suspendCoroutine { con ->
                        MainScope().launch {
                            val function = instance.getFunction("$name")
                            ${callSuperImpl.lines().joinToString("\n                ")}
                            val params = listOf<napi_value>($napiParams).toTypedArray()
                            val promise = function.getNapiValue().call(instance.getNapiValue(), params)
                            val callback = PromiseCallback().apply {
                                onResultListener = {
                                    ${suspendTypeTransform.lines().joinToString("\n                                    ")}
                                }
                                onErrorListener = {
                                    con.resumeWith(Result.failure(RuntimeException("Promise error, message is " + it)))
                                }
                            }
                            loadModule(OhosFFIManager.harName).let {
                                it.namedProperty("getPromiseResultForKN").call(it, arrayOf(promise, callback.createJsObject().napiValue))
                            }
                        }
                    }
                }
            """.trimIndent()
        }
    }

    // 生成 safeSuspend 扩展方法与对应的 WrapperClass
    private fun safeSuspendCode(): String {

        fun getFuncImpl(function: KSFunctionDeclaration): String {
            val name = function.simpleName.asString()
            val kotlinParams = function.parameters.map {
                it.name!!.asString() + ":" + TypeManager.kotlinTypeStr(it.type.resolve())
            }.joinToString(",")
            val callParams = function.parameters.map {
                it.name!!.asString()
            }.joinToString(",")
            return if (isSuspend(function)) {
                """
                    suspend fun $name($kotlinParams): ${TypeManager.kotlinTypeStr(function.returnType!!.resolve())} {
                        return originInstance.$name($callParams)
                    }
                """.trimIndent()
            } else {
                """
                    suspend fun $name($kotlinParams): ${TypeManager.kotlinTypeStr(function.returnType!!.resolve())} {
                        return withContext(Dispatchers.Main.immediate) {
                            originInstance.$name($callParams)
                        }
                    }
                """.trimIndent()
            }
        }

        return """
            fun $className.safeSuspend(): ${safeSuspendWrapperClass} {
                return ${safeSuspendWrapperClass}(this)
            }
            
            class ${safeSuspendWrapperClass}(val originInstance: $className) {
                ${exportFunctions.map {getFuncImpl(it)}.joinToString("\n").lines().joinToString("\n                ")}
            }
        """.trimIndent()
    }

    override fun createMetaInfo(): ClassMetaInfo {
        return ClassMetaInfo(generateName, DefineMethodInfo(generatePackageName, defineMethodName), emptyList(), exportFunctions.map {
            val name = getCustomName(it) ?: it.simpleName.asString()
            JsBindFunctionGenerator.createFunctionMetaInfo(name, it, null, !it.isAbstract)
        }.toList(), "", true)
    }

}
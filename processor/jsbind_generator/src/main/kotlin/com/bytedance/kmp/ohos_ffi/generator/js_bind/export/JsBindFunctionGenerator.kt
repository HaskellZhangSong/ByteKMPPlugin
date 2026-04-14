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
import com.bytedance.kmp.ohos_ffi.generator.js_bind.DefineMethodInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.FunctionMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.type.TypeManager
import com.bytedance.kmp.ohos_ffi.generator.type.checkPublic
import com.bytedance.kmp.ohos_ffi.generator.type.checkTopLevel
import com.bytedance.kmp.ohos_ffi.generator.type.isSuspend
import com.bytedance.kmp.ohos_ffi.generator.type.nullable
import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

class JsBindFunctionGenerator(val function: KSFunctionDeclaration, environment: SymbolProcessorEnvironment, resolver: Resolver): BaseJsBindGenerator<FunctionMetaInfo>(environment, resolver) {

    companion object {

        const val BRIDGE_METHOD_NAME = "methodBridge"

        const val KSP_MEAT_INFO_KEY = "ksp_meta_info_key_js_bind_function"

        fun generate(environment: SymbolProcessorEnvironment, resolver: Resolver): Int {
            return resolver.getSymbolsWithAnnotation(ANNOTATION_FUNCTION)
                .filterIsInstance<KSFunctionDeclaration>()
                .distinct()
                .apply {
                    forEach { func ->
                        JsBindFunctionGenerator(func, environment, resolver).let {
                            it.check()
                            it.generate()
                            val metaInfo = it.createMetaInfo()
                            KspMetaInfoManager.saveMetaInfo(environment, META_INFO_PACKAGE, "js_bind_function_${func.qualifiedName!!.asString().replace(".", "_")}",
                                KSP_MEAT_INFO_KEY, metaInfo)
                        }
                    }
                }.toList().size
        }

        fun generateBridgeMethod(function: KSFunctionDeclaration, generateName: String = BRIDGE_METHOD_NAME,
                                 receiverCode: String = "",
                                 functionRefCode: String = generateFunctionReference(function)): String {
            var body = ""
            val paramSize = function.parameters.size
            if (paramSize > 0) {
                body += "val params = info!!.params($paramSize)\n"
                // 获取参数
                function.parameters.forEachIndexed { index, param ->
                    if (param.type.resolve().nullable()) {
                        body += """
                                    val arg$index = if (params[$index] == null || params[$index]!!.isUndefined()) {
                                        null
                                    } else {
                                        ${TypeManager.jsObj2KotlinObjCode("params[$index]!!", param.type.resolve(), param.type.resolve().nullable())}
                                    }
                                    
                                """.trimIndent()
                    } else {
                        body += "val arg$index = ${TypeManager.jsObj2KotlinObjCode("params[$index]!!", param.type.resolve(), param.type.resolve().nullable())}\n"
                    }
                }
            }
            val callMethod = "${functionRefCode}(${function.parameters.mapIndexed { index, ksValueParameter ->
                "arg$index"
            }.joinToString(", ")})"
            val returnType = function.returnType?.resolve()?.declaration?.simpleName?.asString()
            val isUnit = returnType == null || returnType == "Unit"
            val resolveCode = if (isUnit) {
                "napi_resolve_deferred(env, deferred.value, getUndefined())"
            } else {
                """
                    var resolveValue: napi_value? = if (result == null) {
                        getUndefined()
                    } else {
                        ${TypeManager.kotlinObj2JsObjCode("result", function.returnType!!.resolve())}
                    }
                    napi_resolve_deferred(env, deferred.value, resolveValue)
                """.trimIndent()
            }
            if (isSuspend(function)) {
                // 调用 suspend 方法，返回 promise
                body +=
                    """
                // 创建 Promise 对象
                val promise = nativeHeap.alloc<napi_valueVar>()
                val deferred = nativeHeap.alloc<napi_deferredVar>()
                napi_create_promise(env, deferred.ptr, promise.ptr)
                $receiverCode

                GlobalScope.launch(Dispatchers.Main.immediate) {
                    // 获取方法结果
                    val result = $callMethod
                    // 回调
                    ${resolveCode.split("\n").joinToString("\n                    ")}
                }

                return promise.value
                """.trimIndent()
            } else {
                // 调用普通 方法
                body += "$receiverCode\n"
                body += "val result = $callMethod\n"
                // 判空
                body += """
                    if (result == null) {
                        return null
                    }
                    
                """.trimIndent()
                // 转换 result 为 js object
                body += "return ${TypeManager.kotlinObj2JsObjCode("result", function.returnType!!.resolve())}"
            }
            return """
            private fun ${generateName}(env: napi_env?, info: napi_callback_info?): napi_value? {
                ${body.lines().joinToString("\n                ")}
            }
            
            
        """.trimIndent()
        }

        fun generateFunctionReference(function: KSFunctionDeclaration): String {
            val functionName = function.simpleName.asString()
            val parent = function.parent
            if (parent is KSFile) {
                return functionName
            }
            if (parent is KSClassDeclaration) {
                if (parent.isCompanionObject) {
                    return "${(parent.parent as KSClassDeclaration).simpleName.asString()}.$functionName"
                } else if (parent.classKind == ClassKind.OBJECT) {
                    return "${parent.simpleName.asString()}.$functionName"
                }
            }
            return ""
        }

        fun createFunctionMetaInfo(name: String, function: KSFunctionDeclaration, defineInfo: DefineMethodInfo? = null, hasDefaultImpl: Boolean = false): FunctionMetaInfo {
            val args = function.parameters.map {
                JsBindPropertyGenerator.createPropertyMetaInfo(it.name!!.asString(), it.type.resolve(), null)
            }
            val returnInfo = JsBindPropertyGenerator.createPropertyMetaInfo("", function.returnType!!.resolve(), null)
            return FunctionMetaInfo(name, args, isSuspend(function), returnInfo, defineInfo, hasDefaultImpl)
        }
    }

    private val packageName = function.packageName.asString()
    private val generatePackageName = "${packageName}.js_bind_function"
    private val generateName = getCustomName(function) ?: function.simpleName.asString()
    private val generateFileName = "JsFunctionBinding_$generateName"
    private val defineMethodName = "defineFunctionFor_${generateName}"

    override fun check() {
        environment.logger.warn("[JsBindFunctionGenerator] start check Function ${getOutputName(function)}")
        checkPackage(function)
        checkTopLevel(function)
        checkPublic(function)
        checkFunctionType(function)
    }

    override fun generate() {
        val types = function.parameters.map { it.type } + function.returnType!!
        val file = environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, generatePackageName, generateFileName)
        file.writer().use {
            it.write(
                importCode(packageName, generatePackageName, types.map { it.resolve() }.toList())
                        + headerCommentCode()
                        + generateBridgeMethod(function)
                        + generateDefineCode()
            )
        }
        println("KSP Processor: OhosFfiProcessor generate new file $generateFileName")
    }

    private fun generateDefineCode(): String {
        return """
                fun ${defineMethodName}(env: napi_env, exports: napi_value) {
                    // 定义 add 方法
                    val descArray = nativeHeap.allocArray<napi_property_descriptor>(1)
                    descArray[0].name = createString("${generateName}")
                    descArray[0].method = staticCFunction { env: napi_env?, info: napi_callback_info? ->
                        ${BRIDGE_METHOD_NAME}(env, info)
                    }
                    descArray[0].attributes = napi_default

                    napi_define_properties(env, exports, 1u, descArray)
                }
                """.trimIndent()
    }

    override fun createMetaInfo(): FunctionMetaInfo {
        return createFunctionMetaInfo(generateName, function, DefineMethodInfo(generatePackageName, defineMethodName))
    }
}
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

package com.bytedance.kmp.ohos_ffi.generator.js_bind

import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindClassGenerator.Companion.BIND_CLASS_PACKAGE_SUFFIX
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindInterfaceGenerator
import com.bytedance.kmp.ohos_ffi.generator.type.TypeEnum
import com.bytedance.kmp.ohos_ffi.generator.type.TypeManager
import com.bytedance.kmp.ohos_ffi.generator.type.collections.MapHandler
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import kotlinx.serialization.Serializable

abstract class BaseJsBindGenerator<MetaInfo>(val environment: SymbolProcessorEnvironment, val resolver: Resolver) {

    companion object {
        const val ANNOTATION_PROPERTY = "com.bytedance.kmp.ohos_ffi.annotation.KotlinExportProperty"
        const val ANNOTATION_FUNCTION = "com.bytedance.kmp.ohos_ffi.annotation.KotlinExportFunction"
        const val ANNOTATION_CLASS = "com.bytedance.kmp.ohos_ffi.annotation.KotlinExportClass"
        const val ANNOTATION_CLASS_GENERATOR = "com.bytedance.kmp.ohos_ffi.annotation.KotlinExportClassGenerator"
        const val ANNOTATION_ENUM = "com.bytedance.kmp.ohos_ffi.annotation.KotlinExportEnum"
        const val ANNOTATION_EXPORT = "com.bytedance.kmp.ohos_ffi.annotation.KotlinExport"
        const val ANNOTATION_NAME = "com.bytedance.kmp.ohos_ffi.annotation.KotlinExportName"
        const val ANNOTATION_SO_INIT_FUNC = "com.bytedance.kmp.ohos_ffi.annotation.ArkSoInitFunction"
        const val ANNOTATION_ARK_IMPORT_INTERFACE = "com.bytedance.kmp.ohos_ffi.annotation.KotlinExportInterface"
        const val ANNOTATION_ARK_THREAD_SAFE = "com.bytedance.kmp.ohos_ffi.annotation.KotlinThreadSafe"

        const val META_INFO_PACKAGE = "com.bytedance.kmp.ohos_ffi.generated_meta_info"

        /**
         * 检查包名是否为空，不允许空包名
         */
        fun checkPackage(declaration: KSDeclaration) {
            if (declaration.packageName.asString().isEmpty()) {
                throw RuntimeException("[BaseJsBindGenerator] package name of ${getOutputName(declaration)} is empty")
            }
        }

        /**
         * 检查属性的类型是否基础类型或标注为 @KotlinExportClass 的类
         */
        fun checkPropertyType(property: KSPropertyDeclaration) {
            val type = property.type.resolve()
            if (!TypeManager.isSupportClass(type)) {
                throw RuntimeException("type of ${getOutputName(property)}(${type.declaration.qualifiedName?.asString()}) is not support")
            }
            if (isArkTsInterfaceType(type)) {
                throw RuntimeException("type of ${getOutputName(property)}(${type.declaration.qualifiedName?.asString()}) can not be @KotlinExportInterface")
            }
        }

        /**
         * 1. 入参不能为 napi_valueVar，只能使用 napi_value
         *
         * 2. 对于给 Kotlin 调用的方法，实现在 ArkTs，，需要检查
         *  a. 参数必须为基础类型或标注为 @KotlinExportClass 的类
         *  b. 返回值必须为基础类型或标注为 @KotlinExportClass 的类
         *
         * 3. 对于给 ArkTs 调用的方法，实现在 Kotlin，，需要检查
         *  a. 参数必须为基础类型或标注为 @KotlinExportClass或@KotlinExportInterface 的类
         *  b. 返回值必须为基础类型或标注为 @KotlinExportClass 的类
         */
        fun checkFunctionType(function: KSFunctionDeclaration, callFromKotlin: Boolean = false) {
            function.parameters.forEach {
                val type = it.type.resolve()
                if (!TypeManager.isSupportClass(type)) {
                    throw RuntimeException("param of ${getOutputName(function)}(${type.declaration.qualifiedName?.asString()}) is not Support")
                }
                if (TypeManager.matchType(type, TypeEnum.CUSTOM_CLASS)) {
                    if (callFromKotlin && !isArkTsClassType(type)) {
                        throw RuntimeException("param of kotlin call function ${getOutputName(function)}(${type.declaration.qualifiedName?.asString()}) must be @KotlinExportClass")
                    }
                    if(!callFromKotlin && !isCustomClassType(type)) {
                        throw RuntimeException("param of ark ts call function${getOutputName(function)}(${type.declaration.qualifiedName?.asString()}) must be @KotlinExportClass or @KotlinExportInterface")
                    }
                }
            }
            val returnType = function.returnType!!.resolve()
            if (!TypeManager.isSupportClass(returnType)) {
                throw RuntimeException("return type of ${getOutputName(function)}(${returnType.declaration.qualifiedName?.asString()}) is not Support")
            }
            if (!callFromKotlin && isArkTsInterfaceType(returnType)) {
                throw RuntimeException("return type of ${getOutputName(function)}(${returnType.declaration.qualifiedName?.asString()}) must be @KotlinExportClass")
            }
        }

        fun isObjEqualsFun(it: KSFunctionDeclaration) =
            it.simpleName.asString() == "equals"
                    && it.parameters.size == 1
                    && it.parameters.first().type.resolve().declaration.simpleName.asString() == "Any"

        fun isObjToStringFun(it: KSFunctionDeclaration) =
            it.simpleName.asString() == "toString"
                    && it.parameters.isEmpty()

        fun isObjHashcodeFun(it: KSFunctionDeclaration) =
            it.simpleName.asString() == "hashCode"
                    && it.parameters.isEmpty()

        /**
         * 检查 Class 类型是否标注为 KotlinExportClass
         */
        fun isArkTsClassType(type: KSType): Boolean {
            return type.declaration.annotations.any {
                it.annotationType.resolve().declaration
                    .qualifiedName?.asString() == ANNOTATION_CLASS
            }
        }

        /**
         * 检查 Class 类型是否标注为 KotlinExportInterface
         */
        fun isArkTsInterfaceType(type: KSType): Boolean {
            return isArkTsInterfaceType(type.declaration)
        }

        fun isArkTsInterfaceType(declaration: KSDeclaration): Boolean {
            return declaration.annotations.any {
                it.annotationType.resolve().declaration
                    .qualifiedName?.asString() == ANNOTATION_ARK_IMPORT_INTERFACE
            }
        }

        fun isCustomClassType(type: KSType): Boolean {
            return isArkTsClassType(type) || isArkTsInterfaceType(type)
        }

        fun getCustomName(declaration: KSDeclaration): String? {
            val anno = declaration.annotations.firstOrNull {
                it.annotationType.resolve().declaration
                    .qualifiedName?.asString() == ANNOTATION_NAME
            } ?: return null
            return anno.arguments.firstOrNull {
                it.name?.asString() == "name"
            }?.value as? String
        }

        fun getOutputName(declaration: KSDeclaration): String {
            return declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
        }

        fun importCode(packageName: String, generatePackageName: String, types: List<KSType> = emptyList()): String {
            return """
                @file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "FunctionName")
                @file:OptIn(ExperimentalForeignApi::class)
        
                package ${generatePackageName}
        
                import com.bytedance.kmp.ohos_ffi.*
                import com.bytedance.kmp.ohos_ffi.debug.*
                import com.bytedance.kmp.ohos_ffi.napi.*
                import com.bytedance.kmp.ohos_ffi.napi.types.*
                import com.bytedance.kmp.ohos_ffi.napi.wrap.*
                import com.bytedance.kmp.ohos_ffi.types.*
                import com.bytedance.kmp.ohos_ffi.napi.ref.*
                import com.bytedance.kmp.ohos_ffi.promise.*
                import com.bytedance.kmp.ohos_ffi.promise.js_bind_class.*
                import kotlinx.cinterop.ExperimentalForeignApi
                import kotlinx.cinterop.*
                import kotlin.coroutines.*
                import platform.ohos.napi.*
                import ${packageName}.*
                import kotlinx.coroutines.*
                import platform.ohos.hilog.*
                import platform.linux.strlen
                
                """.trimIndent() +
                    types.map {
                        // 泛型
                        listOf(it) + it.arguments.mapNotNull { it.type?.resolve() }
                    }.flatten().mapNotNull { getFinalCustomParamType(it) }.map {
                        // 对非基础类型的引用，需要引入生成的 js_bind_class 包名下的方法
                        customClassGetObjImport(it)
                    }.flatten().toSet().joinToString("\n") + "\n\n"
        }

        /**
         * 获取集合类型包含的自定义类型信息
         * Map<String, List<A>> -> A
         * Map<String, A> -> A
         * List<A> -> A
         */
        fun getFinalCustomParamType(type: KSType): KSType? {
            if (TypeManager.matchType(type, TypeEnum.CUSTOM_CLASS, TypeEnum.ENUM_CLASS)) {
                return type
            }
            if (TypeManager.matchType(type, TypeEnum.COLLECTION)) {
                val argumentType = if (MapHandler.match(type)) type?.arguments?.getOrNull(1)?.type?.resolve() else type?.arguments?.firstOrNull()?.type?.resolve()
                argumentType ?: return null
                return getFinalCustomParamType(argumentType)
            }
            return null
        }

        private fun customClassGetObjImport(type: KSType): List<String> {
            val getInstanceImport = if (isArkTsClassType(type)) {
                "import ${type.declaration.packageName.asString()}.${BIND_CLASS_PACKAGE_SUFFIX}.*"
            } else if (isArkTsInterfaceType(type)) {
                "import ${type.declaration.packageName.asString()}.${JsBindInterfaceGenerator.PACKAGE_SUFFIX}.*"
            } else {
                ""
            }
            return listOf(
                getInstanceImport,
                "import ${type.declaration.packageName.asString()}.${type.declaration.simpleName.asString()}"
            )
        }

        fun headerCommentCode(): String {
            return """
                
                """.trimIndent()
        }
    }

    /**
     * 合法性检查
     */
    abstract fun check()

    /**
     * 生成 Binding 代码
     */
    abstract fun generate()

    /**
     * 生成 meta info
     */
    abstract fun createMetaInfo(): MetaInfo?

}


/**
 * type:
 * 1. 基础类型
 * kotlin.Int
 * 2. 非基础类型
 * XXX
 */
@Serializable
class MetaInfoLocalCache(val classes: List<ClassMetaInfo>,
                         val properties: List<PropertyMetaInfo>,
                         val functions: List<FunctionMetaInfo>,
                         val importInterfaces: List<ClassMetaInfo>,
                         val enums: List<EnumMetaInfo> = emptyList())
@Serializable
data class DefineMethodInfo(val definePackage: String, val defineMethodName: String)
@Serializable
data class PropertyMetaInfo(val name: String, val defineMethod: DefineMethodInfo? = null,
                            val nullable: Boolean = false, val readOnly: Boolean = false, val jsTypeStr: String, val isCustomType: Boolean = false,
                            // 泛型类型
                            val paramsType: String = "", val customTransform: Boolean = false)
@Serializable
data class FunctionMetaInfo(val name: String,
                            val args: List<PropertyMetaInfo>? = null,
                            val isSuspend: Boolean = false,
                            val returnType: PropertyMetaInfo? = null,
                            val defineMethod: DefineMethodInfo? = null,
                            // 生成的接口是否有默认实现
                            val hasDefaultImpl: Boolean = false)
@Serializable
data class ClassMetaInfo(
    val name: String,
    val defineMethod: DefineMethodInfo,
    val properties: List<PropertyMetaInfo>? = null,
    val functions: List<FunctionMetaInfo>? = null,
    val singletonName: String = "",
    val isImportInterface: Boolean = false,
    val constructor: FunctionMetaInfo? = null,
)

@Serializable
data class EnumMetaInfo(
    val name: String,
    val values: List<String>,
    val defineMethod: DefineMethodInfo,
)

@Serializable
data class CustomSoInitFuncMetaInfo(val packageName: String, val funcName: String, val priority: Int = Int.MAX_VALUE)
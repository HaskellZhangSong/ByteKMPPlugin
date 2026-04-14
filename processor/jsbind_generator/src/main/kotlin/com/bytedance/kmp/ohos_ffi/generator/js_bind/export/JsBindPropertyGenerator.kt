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
import com.bytedance.kmp.ohos_ffi.generator.js_bind.PropertyMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.customtransform.CustomTransformManager
import com.bytedance.kmp.ohos_ffi.generator.type.TypeEnum
import com.bytedance.kmp.ohos_ffi.generator.type.TypeManager
import com.bytedance.kmp.ohos_ffi.generator.type.checkPublic
import com.bytedance.kmp.ohos_ffi.generator.type.checkTopLevel
import com.bytedance.kmp.ohos_ffi.generator.type.nullable
import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType

class JsBindPropertyGenerator(val property: KSPropertyDeclaration, environment: SymbolProcessorEnvironment, resolver: Resolver): BaseJsBindGenerator<PropertyMetaInfo>(environment, resolver) {
    companion object {

        const val SETTER_METHOD_NAME = "setterMethod"
        const val GETTER_METHOD_NAME = "getterMethod"

        const val KSP_MEAT_INFO_KEY = "ksp_meta_info_key_js_bind_property"

        fun generate(environment: SymbolProcessorEnvironment, resolver: Resolver): Int {
            return resolver.getSymbolsWithAnnotation(ANNOTATION_PROPERTY)
                .filterIsInstance<KSPropertyDeclaration>()
                .distinct()
                .apply {
                    forEach { property ->
                        JsBindPropertyGenerator(property, environment, resolver).let {
                            it.check()
                            it.generate()
                            val metaInfo = it.createMetaInfo()
                            KspMetaInfoManager.saveMetaInfo(environment, META_INFO_PACKAGE, "js_bind_property_${property.qualifiedName!!.asString().replace(".", "_")}",
                                KSP_MEAT_INFO_KEY, metaInfo)
                        }
                    }
                }.toList().size
        }

        /**
         * 生成获取属性的 kotlin 代码
         * 1. 如果是顶层属性则返回属性名称
         * 2. 如果是 Object 或 CompanionObject则返回外部类名称
         */
        fun propertyReferenceCode(property: KSPropertyDeclaration): String {
            val propertyName = property.simpleName.asString()
            val parent = property.parent
            if (parent is KSFile) {
                return propertyName
            }
            if (parent is KSClassDeclaration) {
                if (parent.isCompanionObject) {
                    return "${(parent.parent as KSClassDeclaration).simpleName.asString()}.$propertyName"
                } else {
                    return "${parent.simpleName.asString()}.$propertyName"
                }
            }
            return ""
        }

        fun generateSetterCode(prop: KSPropertyDeclaration, type: KSType,
                               generateName: String = SETTER_METHOD_NAME, propertyReferenceCode: String = propertyReferenceCode(prop)): String {
            if (prop.setter == null) {
                // 只读属性，不做任何操作
                return """
                private fun ${generateName}(env: napi_env?, info: napi_callback_info?): napi_value? {
                    return getUndefined()
                }
                
                """.trimIndent()
            }
            val nullable = type.nullable()
            val nullableCode = if (nullable) {
                """
                    if (value.isUndefined()) {
                        ${propertyReferenceCode} = null
                        return getUndefined()
                    }
                """.trimIndent()
            } else {
                ""
            }
            return """
                private fun ${generateName}(env: napi_env?, info: napi_callback_info?): napi_value? {
                    val value = info!!.param(0)
                    ${nullableCode.lines().joinToString("\n                    ")}
                    ${propertyReferenceCode} = ${TypeManager.jsObj2KotlinObjCode("value", type, type.nullable())}
                    return getUndefined()
                }
                
                """.trimIndent()
        }

        fun generateGetterCode(prop: KSPropertyDeclaration, type: KSType,
                               generateName: String = GETTER_METHOD_NAME, propertyReferenceCode: String = propertyReferenceCode(prop)): String {
            return """
                private fun ${generateName}(env: napi_env?, info: napi_callback_info?): napi_value? {
                    val value = ${propertyReferenceCode}
                    if (value == null) {
                        return null
                    }
                    return ${TypeManager.kotlinObj2JsObjCode("value", type)}
                }
                
                """.trimIndent()
        }

        fun createPropertyMetaInfo(name: String, type: KSType, defineInfo: DefineMethodInfo?, readOnly: Boolean = false): PropertyMetaInfo {
            // 泛型
            val argumentTypeStr = getFinalCustomParamType(type)?.let {
                TypeManager.jsTypeStr(it)
            } ?: ""
            return PropertyMetaInfo(name, defineInfo,
                type.nullable(), readOnly, TypeManager.jsTypeStr(type), TypeManager.matchType(type, TypeEnum.CUSTOM_CLASS, TypeEnum.ENUM_CLASS), argumentTypeStr, CustomTransformManager.useCustomTransformer(type.declaration))
        }
    }

    private val type = property.type.resolve()
    private val packageName = property.packageName.asString()
    private val generateName = getCustomName(property) ?: property.simpleName.asString()
    val generatePackageName = "${property.packageName.asString()}.js_bind_property"
    private val generateFileName = "JsPropertyBinding_${generateName}"
    private val defineMethodName = "definePropertyFor_${generateName}"

    /**
     * 合法性检查
     */
    override fun check() {
        checkPackage(property)
        checkTopLevel(property)
        checkPublic(property)
        checkPropertyType(property)
    }

    override fun generate() {
        val file = environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, generatePackageName, generateFileName)
        file.writer().use {
            it.write(
                importCode(packageName, generatePackageName, listOf(type))
                        + headerCommentCode()
                        + generateSetterCode(property, type)
                        + generateGetterCode(property, type)
                        + generateDefineCode()
            )
        }
        println("KSP Processor: OhosFfiProcessor generate new file $generateFileName")
    }

    fun generateDefineCode(): String {
        return """

                fun ${defineMethodName}(env: napi_env, exports: napi_value) {
                    // 定义 add 方法
                    val descArray = nativeHeap.allocArray<napi_property_descriptor>(1)
                    descArray[0].name = createString("${generateName}")
                    descArray[0].setter = staticCFunction { env: napi_env?, info: napi_callback_info? ->
                        ${SETTER_METHOD_NAME}(env, info)
                    }
                    descArray[0].getter = staticCFunction { env: napi_env?, info: napi_callback_info? ->
                        ${GETTER_METHOD_NAME}(env, info)
                    }
                    descArray[0].attributes = napi_default

                    napi_define_properties(env, exports, 1u, descArray)
                }
                """.trimIndent()
    }

    override fun createMetaInfo(): PropertyMetaInfo {
        return createPropertyMetaInfo(generateName, property.type.resolve(), DefineMethodInfo(generatePackageName, defineMethodName), property.setter == null)
    }

}
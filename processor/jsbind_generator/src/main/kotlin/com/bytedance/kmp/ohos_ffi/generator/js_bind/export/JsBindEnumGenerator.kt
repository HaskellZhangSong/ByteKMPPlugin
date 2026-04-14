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
import com.bytedance.kmp.ohos_ffi.generator.js_bind.EnumMetaInfo
import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile

class JsBindEnumGenerator(val clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment, resolver: Resolver): BaseJsBindGenerator<EnumMetaInfo>(environment, resolver) {

    companion object {

        const val BIND_ENUM_PACKAGE_SUFFIX = "js_bind_enum"
        const val KSP_MEAT_INFO_KEY = "ksp_meta_info_key_js_bind_enum"

        fun generate(environment: SymbolProcessorEnvironment, resolver: Resolver): Int {
            return resolver.getSymbolsWithAnnotation(ANNOTATION_ENUM)
                .filterIsInstance<KSClassDeclaration>()
                .distinct()
                .apply {
                    forEach { clazz ->
                        JsBindEnumGenerator(clazz, environment, resolver).let {
                            it.check()
                            it.generate()
                            val metaInfo = it.createMetaInfo() ?: return@forEach
                            KspMetaInfoManager.saveMetaInfo(environment, META_INFO_PACKAGE, "js_bind_enum_${clazz.qualifiedName!!.asString().replace(".", "_")}",
                                KSP_MEAT_INFO_KEY, metaInfo)
                        }
                    }
                }.toList().size
        }
    }

    private val packageName = clazz.packageName.asString()
    private val className = clazz.simpleName.asString()
    private val generateName = getCustomName(clazz) ?: clazz.simpleName.asString()
    private val generatePackageName = "$packageName.$BIND_ENUM_PACKAGE_SUFFIX"
    private val defineMethodName = "defineEnumFor${generateName}"
    private val generateFileName = "JsEnumBinding_${className}"

    override fun check() {
        environment.logger.warn("[JsBindEnumGenerator] start check Enum Class ${getOutputName(clazz)}")
        if (clazz.parent !is KSFile) {
            throw RuntimeException("@KotlinExportEnum must be added to a TopLevel Class, ${getOutputName(clazz)}")
        }
        checkPackage(clazz)
        if (clazz.classKind != ClassKind.ENUM_CLASS) {
            throw RuntimeException("@KotlinExportEnum must be added to a Enum Class, ${getOutputName(clazz)}")
        }
    }

    override fun generate() {
        val file = environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, generatePackageName, generateFileName)
        file.writer().use {
            var content = importCode(packageName, generatePackageName) +
                    headerCommentCode() +
                    generateDefineCode()
            it.write(content)
        }
        println("KSP Processor: OhosFfiProcessor generate new file $generateFileName")
    }

    private fun generateDefineCode(): String {
        return """
            fun ${defineMethodName}(env: napi_env, exports: napi_value) {
                val enumObj = nativeHeap.alloc<napi_valueVar>()
                napi_create_object(env, enumObj.ptr)
                
                ${clazz.getEnumEntryNames().mapIndexed { index, s -> "enumObj.setNamedProperty(\"$s\", createInt($index))" }
                    .joinToString("\n                ")}
                
                // napi_object_freeze使对象不可修改，模拟枚举的不可变性
                napi_object_freeze(env, enumObj.value)
                
                val descArray = nativeHeap.allocArray<napi_property_descriptor>(1)
                descArray[0].name = createString("$generateName")
                descArray[0].value = enumObj.value
                descArray[0].attributes = napi_default
            
                napi_define_properties(env, exports, 1u, descArray)
            }
        """.trimIndent()
    }

    override fun createMetaInfo(): EnumMetaInfo {
        return EnumMetaInfo(
            generateName,
            clazz.getEnumEntryNames(),
            DefineMethodInfo(generatePackageName, defineMethodName)
        )
    }

    private fun KSClassDeclaration.getEnumEntryNames(): List<String> {
        if (classKind == ClassKind.ENUM_CLASS) {
            return declarations
                .filterIsInstance<KSClassDeclaration>() // 枚举常量也是 KSClassDeclaration
                .filter { it.classKind == ClassKind.ENUM_ENTRY }
                .map { it.simpleName.asString() }
                .toList()
        }
        return emptyList()
    }
}
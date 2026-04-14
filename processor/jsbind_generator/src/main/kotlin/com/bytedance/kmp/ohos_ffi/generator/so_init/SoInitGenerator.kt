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

package com.bytedance.kmp.ohos_ffi.generator.so_init

import com.bytedance.kmp.ohos_ffi.generator.js_bind.BaseJsBindGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.ClassMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.CustomSoInitFuncMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.EnumMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.CustomSoInitGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.FunctionMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindClassGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindFunctionGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindPropertyGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.MetaInfoLocalCache
import com.bytedance.kmp.ohos_ffi.generator.js_bind.PropertyMetaInfo
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindEnumGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindInterfaceGenerator
import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SoInitGenerator(val environment: SymbolProcessorEnvironment, val resolver: Resolver) {

    companion object {
        const val SO_INIT_PACKAGE = "com.bytedance.kmp.ohos_ffi.init"
        const val SO_INIT_FILE_NAME = "OhosFfiInit"
        const val LOCAL_FILE_NAME = "ohos_ffi_meta_info"
    }

    private var classMetaInfos: List<ClassMetaInfo> = KspMetaInfoManager.getAllMetaInfo<ClassMetaInfo>(resolver, BaseJsBindGenerator.META_INFO_PACKAGE, JsBindClassGenerator.KSP_MEAT_INFO_KEY)
    private var enumMetaInfos: List<EnumMetaInfo> = KspMetaInfoManager.getAllMetaInfo<EnumMetaInfo>(resolver, BaseJsBindGenerator.META_INFO_PACKAGE, JsBindEnumGenerator.KSP_MEAT_INFO_KEY)
    private var functionMetaInfos: List<FunctionMetaInfo> = KspMetaInfoManager.getAllMetaInfo<FunctionMetaInfo>(resolver, BaseJsBindGenerator.META_INFO_PACKAGE, JsBindFunctionGenerator.KSP_MEAT_INFO_KEY)
    private var propertyMetaInfos: List<PropertyMetaInfo> = KspMetaInfoManager.getAllMetaInfo<PropertyMetaInfo>(resolver, BaseJsBindGenerator.META_INFO_PACKAGE, JsBindPropertyGenerator.KSP_MEAT_INFO_KEY)
    private var customSoInitFuncMetaInfos: List<CustomSoInitFuncMetaInfo> = KspMetaInfoManager.getAllMetaInfo<CustomSoInitFuncMetaInfo>(resolver, BaseJsBindGenerator.META_INFO_PACKAGE, CustomSoInitGenerator.KSP_MEAT_INFO_KEY)
    private var importInterfaceMetaInfos: List<ClassMetaInfo> = KspMetaInfoManager.getAllMetaInfo<ClassMetaInfo>(resolver, BaseJsBindGenerator.META_INFO_PACKAGE, JsBindInterfaceGenerator.KSP_MEAT_INFO_KEY)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun process() {
        // 检查是否存在重复的命名
        check()
        generateSoInitCode()
        // 保存 metainfo，用于后续生成 d.ts
        saveMetaInfoToLocal()
    }

    private fun check() {
        val classNames = classMetaInfos.map { it.name } + enumMetaInfos.map { it.name }
        classNames.forEach {
            if (classNames.count { p -> p == it } > 1) {
                throw RuntimeException("more than one Class named '${it}'")
            }
        }

        val functionNames = functionMetaInfos.map { it.name }
        functionNames.forEach {
            if (functionNames.count { p -> p == it } > 1) {
                throw RuntimeException("more than one Function named '${it}'")
            }
        }

        val propertyNames = propertyMetaInfos.map { it.name }
        propertyNames.forEach {
            if (propertyNames.count { p -> p == it } > 1) {
                throw RuntimeException("more than one Property named '${it}'")
            }
        }
    }

    private fun generateSoInitCode() {
        val file = resolver.getAllFiles().firstOrNull {
            it.fileName == "$SO_INIT_FILE_NAME.kt"
        }?.let {
            File(it.filePath).outputStream()
        } ?: environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, SO_INIT_PACKAGE, SO_INIT_FILE_NAME)
        println("KSP Processor: OhosFfiProcessor generate new file $SO_INIT_FILE_NAME")
        if (propertyMetaInfos.isEmpty() && functionMetaInfos.isEmpty() && classMetaInfos.isEmpty() && customSoInitFuncMetaInfos.isEmpty()) {
            // 只生成初始化代码，没有 define 逻辑
            file.writer().use {
                it.write(
                    headerCode()
                            + initCode(true)
                )
            }
            return
        } else {
            file.writer().use {
                it.write(
                    headerCode()
                            + initCode()
                            + defineExportsCode(propertyMetaInfos, functionMetaInfos, classMetaInfos, enumMetaInfos)
                            + soInitCodes(customSoInitFuncMetaInfos)
                )
            }
        }
    }

    private fun headerCode(): String {
        return """
            @file:OptIn(ExperimentalForeignApi::class)
            package $SO_INIT_PACKAGE
            
            import kotlinx.cinterop.*
            import platform.ohos.napi.*
            import com.bytedance.kmp.ohos_ffi.*
            
        """.trimIndent()
    }

    private fun initCode(withEmptyDefine: Boolean = false): String {
        return """
            var hasInit = false
            
            fun init(env: napi_env, exports: napi_value, bundle: String, harName: String) {
                println("KmpLogger: init")
                OhosFFIManager.init(env, exports, bundle, harName)
                if (!hasInit) {
                    println("KmpLogger: defineExports")
                    defineExports(env, exports)
                    hasInit = true
                }
                println("KmpLogger: customSoInit")
                customSoInit(env, exports)
            }
            
            
        """.trimIndent() + if (withEmptyDefine) """
            private fun defineExports(env: napi_env, exports: napi_value) {

            }
            
            private fun customSoInit(env: napi_env, exports: napi_value) {

            }
            
        """.trimIndent() else ""
    }

    private fun defineExportsCode(
        propertyMetaInfos: List<PropertyMetaInfo>,
        functionMetaInfos: List<FunctionMetaInfo>,
        classMetaInfos: List<ClassMetaInfo>,
        enumMetaInfos: List<EnumMetaInfo>,
    ): String {
        val defineMethods = propertyMetaInfos.map { it.defineMethod } +
                functionMetaInfos.map { it.defineMethod } +
                classMetaInfos.map { it.defineMethod } +
                enumMetaInfos.map { it.defineMethod }
        return """
            private fun defineExports(env: napi_env, exports: napi_value) {
                ${
            defineMethods.map { "${it!!.definePackage}.${it!!.defineMethodName}(env, exports)" }
                .joinToString("\n                ")
        }
            }
            
            
        """.trimIndent()
    }

    private fun soInitCodes(
        customSoInitFuncMetaInfo: List<CustomSoInitFuncMetaInfo>,
    ): String {
        return """
            private fun customSoInit(env: napi_env, exports: napi_value) {
                ${
            customSoInitFuncMetaInfo.sortedBy { it.priority }.map { "${it!!.packageName}.${it!!.funcName}(env, exports)" }
                .joinToString("\n                ")
        }
            }
        """.trimIndent()
    }

    private fun saveMetaInfoToLocal() {

        if (propertyMetaInfos.isEmpty() && functionMetaInfos.isEmpty() && classMetaInfos.isEmpty() && importInterfaceMetaInfos.isEmpty() && enumMetaInfos.isEmpty()) {
            return
        }
        val file = resolver.getAllFiles().firstOrNull {
            it.fileName == "$LOCAL_FILE_NAME.kt"
        }?.let {
            File(it.filePath).outputStream()
        } ?: environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, "", LOCAL_FILE_NAME)
        file.writer().use {
            val jsonStr = json.encodeToString(MetaInfoLocalCache(classMetaInfos, propertyMetaInfos, functionMetaInfos, importInterfaceMetaInfos, enumMetaInfos))
            // 因为生成的是 json 而不是 kotlin 代码会在编译阶段失败，所以整体加上注解绕过编译
            it.write("/*\n${jsonStr}\n*/")
        }
        println("KSP Processor: OhosFfiProcessor generate new file $LOCAL_FILE_NAME")
    }
}
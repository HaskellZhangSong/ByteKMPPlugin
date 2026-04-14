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
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.customtransform.CustomTransformManager
import com.bytedance.kmp.ohos_ffi.generator.type.TypeManager
import com.bytedance.kmp.ohos_ffi.generator.type.checkPublic
import com.bytedance.kmp.ohos_ffi.generator.type.nullable
import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

class JsBindClassGenerator(val clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment, resolver: Resolver): BaseJsBindGenerator<ClassMetaInfo>(environment, resolver) {

    companion object {

        const val ANNOTATION_CUSTOM_TRANSFORM = "com.bytedance.kmp.ohos_ffi.annotation.ArkTsExportCustomTransform"
        const val CUSTOM_TRANSFORMER_INTERFACE = "com.bytedance.kmp.ohos_ffi.transform.ArkTsExportCustomTransformer"

        const val CREATE_JS_OBJ_METHOD_NAME = "createJsObject"
        const val CONSTRUCTOR_METHOD_NAME = "constructor"
        const val BIND_CLASS_PACKAGE_SUFFIX = "js_bind_class"
        const val KSP_MEAT_INFO_KEY = "ksp_meta_info_key_js_bind_class"

        fun generate(environment: SymbolProcessorEnvironment, resolver: Resolver): Int {
            return resolver.getSymbolsWithAnnotation(ANNOTATION_CLASS)
                .filterIsInstance<KSClassDeclaration>()
                .distinct()
                .apply {
                    forEach { clazz ->
                        JsBindClassGenerator(clazz, environment, resolver).let {
                            it.check()
                            it.generate()
                            val metaInfo = it.createMetaInfo() ?: return@forEach
                            KspMetaInfoManager.saveMetaInfo(environment, META_INFO_PACKAGE, "js_bind_class_${clazz.qualifiedName!!.asString().replace(".", "_")}",
                                KSP_MEAT_INFO_KEY, metaInfo)
                        }
                    }
                }.toList().size
        }

        fun getKotlinInstanceMethodName(className: String): String {
            return "get${className}"
        }
    }

    private val packageName = clazz.packageName.asString()
    private val className = clazz.simpleName.asString()
    private val generateName = getCustomName(clazz) ?: clazz.simpleName.asString()
    private val innerObjectName = "InnerObject_$className"
    private val generatePackageName = "$packageName.$BIND_CLASS_PACKAGE_SUFFIX"
    private val defineMethodName = "defineClassFor${generateName}"
    private val generateFileName = "JsClassBinding_${className}"
    private val getKotlinInstanceMethodName = getKotlinInstanceMethodName(className)

    private val exportProperties = clazz.getAllProperties().filter {
        it.annotations.any {
            it.annotationType.resolve().declaration
                .qualifiedName?.asString() == ANNOTATION_EXPORT
        }
    }

    // [generateName: <Setter, Getter>]
    private val propertyGeneratedMethods = mutableMapOf<String, Pair<String, String>>()

    private val exportFunctions = clazz.getAllFunctions().filter {
        it.annotations.any {
            it.annotationType.resolve().declaration
                .qualifiedName?.asString() == ANNOTATION_EXPORT
        }
    }

    // [generateName: bridgeMethod]
    private val functionGeneratedMethods = mutableMapOf<String, String>()

    private val generatorFunction = getGeneratorFunction()

    private var customTransformer: String? = null

    private fun getGeneratorFunction(): KSFunctionDeclaration? {
        // 首先在伴生对象中寻找
        var generateFunction = clazz.declarations.filterIsInstance<KSClassDeclaration>().filter {
            it.isCompanionObject
        }.firstOrNull()?.let {
            it.getAllFunctions().filter {func ->
                func.annotations.any {
                    it.annotationType.resolve().declaration
                        .qualifiedName?.asString() == ANNOTATION_CLASS_GENERATOR
                }
            }.firstOrNull()
        }
        if (generateFunction == null) {
            // 然后在构造函数中寻找
            generateFunction = clazz.getConstructors().filter {func ->
                func.annotations.any {
                    it.annotationType.resolve().declaration
                        .qualifiedName?.asString() == ANNOTATION_CLASS_GENERATOR
                }
            }.firstOrNull()
        }
        return generateFunction
    }

    override fun check() {
        environment.logger.warn("[JsBindClassGenerator] start check Class ${getOutputName(clazz)}")
        if (clazz.classKind == ClassKind.ENUM_CLASS) {
            throw RuntimeException("@KotlinExportClass can not be added with Enum Class, please use @KotlinExportEnum instead, ${getOutputName(clazz)}")
        }
        if (clazz.parent !is KSFile) {
            throw RuntimeException("@KotlinExportClass must be added to a TopLevel Class, ${getOutputName(clazz)}")
        }
        checkPackage(clazz)
        // 检查当前类和需要导出的字段、方法是否 public
        (exportProperties + exportFunctions + clazz).forEach {
            checkPublic(it)
        }
        // 检查类型
        exportFunctions.forEach {
            checkFunctionType(it)
        }
        exportProperties.forEach {
            checkPropertyType(it)
        }
        // 检查重复 key
        val propertiesName = exportProperties.map { getCustomName(it) ?: it.simpleName.asString() }
        propertiesName.forEach {
            if (propertiesName.count { p -> p == it } > 1) {
                throw RuntimeException("more than one properties named '${it}'")
            }
        }
        val functionsName = exportFunctions.map { getCustomName(it) ?: it.simpleName.asString() }
        functionsName.forEach {
            if (functionsName.count { p -> p == it } > 1) {
                throw RuntimeException("more than one functions named '${it}'")
            }
        }
        // 检查构造方法的参数与返回值
        if (generatorFunction != null) {
            generatorFunction.parameters.forEach {
                if (!TypeManager.isSupportClass(it.type.resolve())) {
                    throw RuntimeException("generatorFunction ${getOutputName(generatorFunction)} param '${it.name!!.asString()}' type must be supported type")
                }
            }
            if (!generatorFunction.isConstructor() && generatorFunction.returnType?.resolve()?.declaration?.qualifiedName?.asString() != clazz.qualifiedName?.asString()) {
                throw RuntimeException("generatorFunction ${getOutputName(generatorFunction)} return type must be ${clazz.qualifiedName?.asString()}")
            }
        }
        if (getSingletonName().isNotEmpty() && (generatorFunction?.parameters?.size ?: 0) > 0) {
            throw RuntimeException("Constructor/GeneratorFunc of Singleton Class cannot have params, ${getOutputName(clazz)}")
        }

        customTransformer = CustomTransformManager.getTransformer(clazz.qualifiedName!!.asString())?.transformer
    }

    override fun generate() {
        val generatorFunctionList = if (generatorFunction == null) emptyList() else listOf(generatorFunction)
        val types = exportProperties.map { it.type } +
                (exportFunctions + generatorFunctionList).map { it.parameters.map { it.type } }.flatten() +
                (exportFunctions + generatorFunctionList).map { it.returnType!! }
        val file = environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, generatePackageName, generateFileName)
        file.writer().use {
            var content = importCode(packageName, generatePackageName, types.map { it.resolve() }.toList()) +
                    headerCommentCode()
            if (!useCustomTransformer()) {
                content += generateGetJsObjCode() +
                        generateGetKotlinObjCode() +
                        generateConstructorCode() +
                        generatePropertiesCode() +
                        generateFunctionsCode() +
                        generateDefineCode()
            } else {
                // 使用自定义的 Transformer，只需要转换逻辑即可
                content += generateGetJsObjCode() +
                        generateGetKotlinObjCode()
            }
            it.write(content)
        }
        println("KSP Processor: OhosFfiProcessor generate new file $generateFileName")
    }

    private fun generateGetJsObjCode(): String {
        return if (!useCustomTransformer()) {
            """
                // 用于直接绑定 JS 对象，避免无效的构造、 wrap 、 unwrap 操作
                private var pendingKNObject: $className? = null
                
                /**
                 * 创建一个 Kotlin Instance 绑定的 JS 对象
                 */
                fun $className.${CREATE_JS_OBJ_METHOD_NAME}(): ArkInstance {
                    // 通过 napi 创建 js 侧的对象
                    pendingKNObject = this
                    val constructor = loadModule(OhosFFIManager.harName).namedProperty("$generateName")
                    return ArkInstance(newInstance(constructor))
                }
                
                
            """.trimIndent()
        } else {
            """
                /**
                 * 创建一个 Kotlin Instance 绑定的 JS 对象
                 */
                fun $className.${CREATE_JS_OBJ_METHOD_NAME}(): ArkInstance {
                    val jsObject = $customTransformer.toJsObject(this)
                    return ArkInstance(jsObject)
                }
                
                
            """.trimIndent()
        }
    }

    private fun generateGetKotlinObjCode(): String {
        return if (!useCustomTransformer()) {
            """
                fun napi_value.${getKotlinInstanceMethodName}(): ${className}? {
                    if (isUndefined()) {
                        return null
                    }
                    // 获取 Kotlin 对象
                    val instance = unwrap()?.asStableRef<${className}>()?.get()
                    return instance
                }
                
                
            """.trimIndent()
        } else {
            """
                fun napi_value.${getKotlinInstanceMethodName}(): ${className}? {
                    if (isUndefined()) {
                        return null
                    }
                    return $customTransformer.fromJsObject(this)
                }
                
                
            """.trimIndent()
        }
    }

    private fun generateConstructorCode(): String {
        return if (!noConstructor()) {
            """
            private object $innerObjectName {
                fun ${CONSTRUCTOR_METHOD_NAME}(env: napi_env?, info: napi_callback_info?): napi_value? {
                    // 获取当前对象的 this
                    val thisArg = info!!.thisArg()
                    val kmpClassInstance = if (pendingKNObject != null) {
                        pendingKNObject!!
                    } else {
                        // 创建 KN 对象
                        ${createKotlinObjectCode("info").lines().joinToString("\n                    ")}
                    }
                    pendingKNObject = null
                    val stableRef = StableRef.create(kmpClassInstance).asCPointer()
                    DebugRefCnt.addStableRefCnt("$className with constructor(" + stableRef.rawValue + ">" + thisArg.rawValue + ")")
                    napi_wrap(env, thisArg, stableRef, staticCFunction { env: napi_env?, data: COpaquePointer?, hint: COpaquePointer? ->
                        kmpClassFinalize(env, data, hint)
                    }, null, null)
                    // 返回构造参数
                    return thisArg
                }
                
                fun kmpClassFinalize(env: napi_env?, data: COpaquePointer?, hint: COpaquePointer?) {
                    data ?: return print("error finalize input null data")
                    DebugRefCnt.reduceStableRefCnt("$className with constructor")
                    val stableRef = data.asStableRef<${className}>()
                    stableRef.dispose()
                }
            }
            
            
        """.trimIndent()
        } else {
            """
                private object $innerObjectName {
                    fun ${CONSTRUCTOR_METHOD_NAME}(env: napi_env?, info: napi_callback_info?): napi_value? {
                        // 获取当前对象的 this
                        val thisArg = info!!.thisArg()
                        if (pendingKNObject != null) {
                            val stableRef = StableRef.create(pendingKNObject!!).asCPointer()
                            DebugRefCnt.addStableRefCnt("$className(" + stableRef.rawValue + ">" + thisArg.rawValue + ")")
                            napi_wrap(env, thisArg, stableRef, staticCFunction { env: napi_env?, data: COpaquePointer?, hint: COpaquePointer? ->
                                kmpClassFinalize(env, data, hint)
                            }, null, null)
                            pendingKNObject = null
                        }
                        // 返回构造参数
                        return thisArg
                    }
                    
                    fun kmpClassFinalize(env: napi_env?, data: COpaquePointer?, hint: COpaquePointer?) {
                        data ?: return print("error finalize input null data")
                        DebugRefCnt.reduceStableRefCnt("$className")
                        val stableRef = data.asStableRef<${className}>()
                        stableRef.dispose()
                    }
                }
                
                
            """.trimIndent()
        }
    }

    private fun createKotlinObjectCode(callInfoName: String): String {
        // 如果在类的伴生对象中指定了获取对象的方法，则使用该方法
        if (generatorFunction != null) {
            return createGeneratorFunctionCallCode(callInfoName)
        }
        // 如果当前类是 object 类型，则直接返回当前类名
        if (clazz.classKind == ClassKind.OBJECT) {
            return "$className"
        }
        // 通过无参数构造函数创建
        return "${className}()"
    }

    private fun createGeneratorFunctionCallCode(callInfoName: String): String {
        generatorFunction!!
        val params = generatorFunction.parameters
        val paramsStr = params.mapIndexed { index, it ->
            if (it.type.resolve().nullable()) {
                """
                    val param$index = $callInfoName.param($index)
                    val realParam$index = if (param$index == null || param$index.isUndefined()) {
                        null
                    } else {
                        ${TypeManager.jsObj2KotlinObjCode("param$index", it.type.resolve(), it.type.resolve().nullable())}
                    }
                """.trimIndent()
            } else {
                "val realParam$index = ${TypeManager.jsObj2KotlinObjCode("$callInfoName.param($index)", it.type.resolve(), it.type.resolve().nullable())}"
            }
        }.joinToString("\n")
        val paramNames = params.mapIndexed { index, it ->
            "realParam$index"
        }.joinToString(", ")
        return "$paramsStr\n" + if (generatorFunction.isConstructor()) {
            "$className($paramNames)"
        } else {
            "${className}.${generatorFunction!!.simpleName.asString()}($paramNames)"
        }
    }

    private fun generatePropertiesCode(): String {
        var result = ""
        exportProperties.forEach {
            val propertyName = it.simpleName.asString()
            val getPropertyCode = "info!!.thisArg().$getKotlinInstanceMethodName()?.${propertyName}"
            val name = getCustomName(it) ?: propertyName
            val setterMethodName = "setterMethodFor$name"
            val getterMethodName = "getterMethodFor$name"
            val type = it.type.resolve()
            result += JsBindPropertyGenerator.generateSetterCode(it, type, setterMethodName, getPropertyCode)
            result += "\n"
            result += JsBindPropertyGenerator.generateGetterCode(it, type, getterMethodName, getPropertyCode)
            result += "\n"
            propertyGeneratedMethods[name] = setterMethodName to getterMethodName
        }
        return result
    }

    private fun generateFunctionsCode(): String {
        var result = ""
        exportFunctions.forEach {
            val functionName = it.simpleName.asString()
            val name = getCustomName(it) ?: functionName
            val bridgeMethodName = "bridgeMethodFor$name"
            result += JsBindFunctionGenerator.generateBridgeMethod(it, bridgeMethodName, "val obj = info!!.thisArg().$getKotlinInstanceMethodName()","obj?.${functionName}")
            result += "\n"
            functionGeneratedMethods[name] = bridgeMethodName
        }
        return result
    }

    private fun generateDefineCode(): String {
        val size = exportProperties.count() + exportFunctions.count()
        val defineProperties = exportProperties.mapIndexed { index, it ->
            val name = getCustomName(it) ?: it.simpleName.asString()
            """
                descArray[${index}].name = createString("${name}")
                descArray[${index}].setter = staticCFunction { env: napi_env?, info: napi_callback_info? ->
                    ${propertyGeneratedMethods[name]!!.first}(env, info)
                }
                descArray[${index}].getter = staticCFunction { env: napi_env?, info: napi_callback_info? ->
                    ${propertyGeneratedMethods[name]!!.second}(env, info)
                }
                descArray[${index}].attributes = napi_default
            """.trimIndent()
        }.joinToString("\n")

        val offset = exportProperties.count()
        val defineFunctions = exportFunctions.mapIndexed { index, it ->
            val name = getCustomName(it) ?: it.simpleName.asString()
            """
                descArray[${index + offset}].name = createString("${name}")
                descArray[${index + offset}].method = staticCFunction { env: napi_env?, info: napi_callback_info? ->
                    ${functionGeneratedMethods[name]}(env, info)
                }
                descArray[${index + offset}].attributes = napi_default
            """.trimIndent()
        }.joinToString("\n")

        val result = """
            fun ${defineMethodName}(env: napi_env, exports: napi_value) {
                val descArray = nativeHeap.allocArray<napi_property_descriptor>(${size})
                // 定义 Property
                ${defineProperties.lines().joinToString("\n                ")}

                // 定义 Function
                ${defineFunctions.lines().joinToString("\n                ")}

                // 定义 Class
                val result = nativeHeap.alloc<napi_valueVar>()
                napi_define_class(env, "${generateName}", strlen("${generateName}"), staticCFunction { env: napi_env?, info: napi_callback_info? ->
                    $innerObjectName.${CONSTRUCTOR_METHOD_NAME}(env, info)
                }, null, ${size}u, descArray, result.ptr)
                napi_set_named_property(env, exports, "${generateName}", result.value)
            }
        """.trimIndent()
        return result
    }

    override fun createMetaInfo(): ClassMetaInfo? {
        if (useCustomTransformer()) {
            // 自定义转换逻辑的类，不用导出到 arkts
            return null
        }
        return ClassMetaInfo(
            generateName,
            DefineMethodInfo(generatePackageName, defineMethodName),
            exportProperties.map {
                val name = getCustomName(it) ?: it.simpleName.asString()
                JsBindPropertyGenerator.createPropertyMetaInfo(name, it.type.resolve(), null, it.setter == null)
            }.toList(),
            exportFunctions.map {
                val name = getCustomName(it) ?: it.simpleName.asString()
                JsBindFunctionGenerator.createFunctionMetaInfo(name, it, null)
            }.toList(),
            getSingletonName(),
            constructor = generatorFunction?.let { JsBindFunctionGenerator.createFunctionMetaInfo("", it)}
        )
    }

    private fun getSingletonName(): String {
        val annotation = clazz.annotations.firstOrNull {
            it.annotationType.resolve().declaration
                .qualifiedName?.asString() == ANNOTATION_CLASS
        }!!
        return annotation.arguments.firstOrNull { it.name?.asString() == "singletonName" }?.value as? String
            ?: ""
    }

    private fun noConstructor(): Boolean {
        val annotation = clazz.annotations.firstOrNull {
            it.annotationType.resolve().declaration
                .qualifiedName?.asString() == ANNOTATION_CLASS
        }!!
        return annotation.arguments.firstOrNull { it.name?.asString() == "noConstructor" }?.value as? Boolean
            ?: false
    }

    private fun useCustomTransformer(): Boolean {
        return CustomTransformManager.useCustomTransformer(clazz)
    }

}
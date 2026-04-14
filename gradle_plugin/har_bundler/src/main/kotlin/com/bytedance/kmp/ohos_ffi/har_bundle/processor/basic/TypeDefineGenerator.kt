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

package com.bytedance.kmp.ohos_ffi.har_bundle.processor.basic

import com.bytedance.kmp.ohos_ffi.har_bundle.utils.createNewFile
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.rewrite
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.Project
import java.io.File

class TypeDefineGenerator(val project: Project, val typesModule: File, val indexFile: File, val soName: String, val enableCompose: Boolean) {

    companion object {
        const val META_INFO_PATH = "generated/ksp/ohosArm64/ohosArm64Main/kotlin/ohos_ffi_meta_info.kt"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun generate() {
        val metaInfo = getMetaInfo() ?: return
        val indexContent = "${commonImport()}\n" +
                "${classDefine(metaInfo.classes, metaInfo.importInterfaces)}\n" +
                "${enumDefine(metaInfo.enums)}\n" +
                "${propertyDefine(metaInfo.properties)}\n" +
                "${functionDefine(metaInfo.functions)}\n"

        // 补充 types目录下的 index.d.ts
        File(typesModule, "index.d.ts").writer().use {
            it.write(indexContent)
        }
        // 将 so 导出的代码在 har 下的 Index.ets 中重导出一遍，用于生成的 Class Binder代码通过 ffi 创建 js 对象
        indexFile.rewrite {
            val exportNames = metaInfo.classes.map { it.name } +
                    metaInfo.enums.map { it.name } +
                    metaInfo.importInterfaces.map { it.name } +
                    metaInfo.functions.map { it.name } +
                    metaInfo.properties.map { it.name }
            exportNames.map {
                "import {$it} from 'lib${soName}.so'"
            }.joinToString("\n") + "\n$it" + "\n//直接导出 so 中的代码，用于 KN 创建 JS 对象\n" + exportNames.map {
                "export {$it}"
            }.joinToString("\n") + if (enableCompose) {
                "\nexport {ComposeView, activeComposeView} from './src/main/ets/compose/ComposeView'"
            } else ""
        }
        indexFile.rewrite {
            // 去掉模版中的 export *，避免 lazy import 失效
            it.replace("export * from 'lib${soName}.so'\n", "")
        }
        // 单例对象在 Index.ets 中导出
        val singletonClasses = metaInfo.classes.filter { it.singletonName.isNotEmpty() }
        if (singletonClasses.isNotEmpty()) {
            indexFile.rewrite {
                it + "\n//导出单例对象\n" + singletonClasses.map {
                    """
                    export const ${it.singletonName}:${it.name} = new ${it.name}()
                """.trimIndent()
                }.joinToString("\n")
            }
        }
    }

    /**
     * 导入 ArkTs 通用的集合类型
     */
    private fun commonImport(): String {
        return """
            import { List } from '@kit.ArkTS';
            
        """.trimIndent()
    }

    private fun propertyDefine(properties: List<PropertyMetaInfo>): String {
        return properties.map {
            if (it.readOnly) {
                "export const ${it.name}: ${getTypeStr(it)};"
            } else {
                "export var ${it.name}: ${getTypeStr(it)};"
            }
        }.joinToString("\n")
    }

    private fun classDefine(classes: List<ClassMetaInfo>, importInterfaces: List<ClassMetaInfo>): String {
        (classes + importInterfaces).forEach { classInfo ->
            // 生成.d.ts代码
            val file = createNewFile(typesModule, "${classInfo.name}.d.ts")
            val classIdentifier = if (classInfo.isImportInterface) {
                "interface"
            } else {
                "class"
            }
            file.writer().use {
                it.write("""
                   ${commonImport().lines().joinToString("\n                   ")}
                   ${customTypeImport(classInfo).lines().joinToString("\n                   ")}
                   export $classIdentifier ${classInfo.name} {
                       ${constructorDefine(classInfo.constructor)}
                       ${classInfo.properties?.map { "${if (it.readOnly) "readonly " else ""}${it.name}: ${getTypeStr(it)};" }?.joinToString("\n                       ") ?: ""}
                       ${functionDefine(classInfo.functions, true, "                       ")}
                    }
                    """.trimIndent())
            }
        }

        var result = (classes + importInterfaces).map {
            """
                import { ${it.name} } from './${it.name}'
                export { ${it.name} }
            """.trimIndent()
        }.joinToString("\n")
        return result
    }

    private fun constructorDefine(constructor: FunctionMetaInfo?): String {
        constructor ?: return ""
        val params = constructor.args?.map {
            "${it.name}: ${getTypeStr(it)}"
        }?.joinToString(", ") ?: ""
        return "constructor($params)"
    }

    private fun customTypeImport(classInfo: ClassMetaInfo): String {
        val allTypes = (classInfo.properties ?: emptyList()) +
                (classInfo.functions?.map { it.args ?: emptyList() }?.flatten() ?: emptyList()) +
                (classInfo.functions?.map { it.returnType } ?: emptyList()) +
                (classInfo.constructor?.args ?: emptyList())
        val typesImport = allTypes.filterNotNull().filter {
            it.isCustomType && !it.customTransform && it.name != classInfo.name
        }.map {
            "import { ${it.jsTypeStr} } from './${it.jsTypeStr}'"
        }
        val paramsTypesImport = allTypes.filterNotNull().filter {
            it.paramsType.isNotEmpty() && it.paramsType != classInfo.name && !it.customTransform
        }.map {
            "import { ${it.paramsType} } from './${it.paramsType}'"
        }

        return (typesImport + paramsTypesImport).toSet().joinToString("\n")
    }
    private fun enumDefine(enums: List<EnumMetaInfo>): String {
        enums.forEach { enumInfo ->
            // 生成.d.ts代码
            val file = createNewFile(typesModule, "${enumInfo.name}.d.ts")
            file.writer().use {
                it.write("""
                   ${commonImport().lines().joinToString("\n                   ")}
                   export enum ${enumInfo.name} {
                       ${enumInfo.values.joinToString(", ") { it }}
                   }
                    """.trimIndent())
            }
        }

        var result = enums.map {
            """
                import { ${it.name} } from './${it.name}'
                export { ${it.name} }
            """.trimIndent()
        }.joinToString("\n")
        return result
    }


    private fun functionDefine(functions: List<FunctionMetaInfo>?, classFunction: Boolean = false, space: String = ""): String {
        functions ?: return ""
        return functions.map {
            val nullableStr = if (classFunction && it.hasDefaultImpl) "?" else ""
            val params = it.args?.map {
                "${it.name}: ${getTypeStr(it)}"
            }?.joinToString(", ") ?: ""
            "${if (classFunction) "" else "export const "}${it.name}$nullableStr: ($params) => ${getTypeStr(it.returnType, it.isSuspend)};"
        }.joinToString("\n$space")
    }

    private fun getMetaInfo(): MetaInfoLocalCache? {
        val metaInfoFile = File(project.buildDir, META_INFO_PATH)
        if (!metaInfoFile.exists()) {
            return null
        }
        val jsonStr = metaInfoFile.readLines().let {
            it.subList(1, it.size - 1)
        }.joinToString("\n")
        return json.decodeFromString<MetaInfoLocalCache>(jsonStr)
    }

    private fun getTypeStr(type: PropertyMetaInfo?, promise: Boolean = false): String {
        if (type == null) {
            return "void"
        }
        var result = type.jsTypeStr+ if (type.nullable) {
            " | undefined"
        } else {""}
        if (promise) {
            result = "Promise<$result>"
        }
        return result
    }
}

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
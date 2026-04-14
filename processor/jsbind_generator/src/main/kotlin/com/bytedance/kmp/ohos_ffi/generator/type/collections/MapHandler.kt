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

package com.bytedance.kmp.ohos_ffi.generator.type.collections

import com.bytedance.kmp.ohos_ffi.generator.type.TypeEnum
import com.bytedance.kmp.ohos_ffi.generator.type.TypeHandler
import com.bytedance.kmp.ohos_ffi.generator.type.TypeManager
import com.bytedance.kmp.ohos_ffi.generator.type.nullable
import com.google.devtools.ksp.symbol.KSType

object MapHandler: TypeHandler() {
    override val type = TypeEnum.COLLECTION

    private val jsObj2KotlinObjMapping = mapOf(
        "kotlin.String" to "asStringMap",
        "kotlin.Int" to "asIntMap",
        "kotlin.Long" to "asLongMap",
        "kotlin.Double" to "asDoubleMap",
        "kotlin.Float" to "asFloatMap",
        "kotlin.Boolean" to "asBooleanMap",
    )

    private val kotlinObj2JsObjMapping = mapOf(
        "kotlin.String" to "createStringMap",
        "kotlin.Int" to "createIntMap",
        "kotlin.Long" to "createLongMap",
        "kotlin.Double" to "createDoubleMap",
        "kotlin.Float" to "createFloatMap",
        "kotlin.Boolean" to "createBooleanMap",
    )

    override fun match(type: KSType): Boolean {
        val typeName = type.declaration.qualifiedName?.asString()!!
        return typeName == "kotlin.collections.Map"
                // 限制 Map 的 key 必须为 String
                && parseParamType(type).first.declaration.qualifiedName?.asString() == "kotlin.String"
    }

    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        val valueType = parseParamType(type).second
        if (TypeManager.matchType(valueType, TypeEnum.CUSTOM_CLASS, TypeEnum.COLLECTION, TypeEnum.NAPI_VALUE, TypeEnum.ENUM_CLASS)) {
            return "$kotlinCode.asMap{ ${TypeManager.jsObj2KotlinObjCode("it", valueType, valueType.nullable())} }"
        } else {
            return "$kotlinCode.${jsObj2KotlinObjMapping[valueType.declaration.qualifiedName?.asString()]}()"
        }
    }

    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        val valueType = parseParamType(type).second
        val result = if (TypeManager.matchType(valueType, TypeEnum.CUSTOM_CLASS, TypeEnum.COLLECTION, TypeEnum.NAPI_VALUE, TypeEnum.ENUM_CLASS)) {
            "createMap($kotlinCode){ ${TypeManager.kotlinObj2JsObjCode("it", valueType, valueType.nullable())} }"
        } else {
            "${kotlinObj2JsObjMapping[valueType.declaration.qualifiedName?.asString()]}($kotlinCode)"
        }
        return result
    }

    override fun jsTypeStr(type: KSType): String {
        val valueType = parseParamType(type).second
        return "Map<string, ${TypeManager.jsTypeStr(valueType)}>"
    }

    override fun kotlinTypeStr(type: KSType): String {
        val valueType = parseParamType(type).second
        return "Map<String, ${TypeManager.kotlinTypeStr(valueType)}>"
    }

    /**
     * 获取泛型信息
     */
    private fun parseParamType(type: KSType): Pair<KSType, KSType> {
        return type.arguments.map { it.type!!.resolve() }.let {
            Pair(it[0], it[1])
        }
    }
}
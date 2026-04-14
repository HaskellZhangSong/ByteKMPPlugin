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

package com.bytedance.kmp.ohos_ffi.generator.type

import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindClassGenerator
import com.bytedance.kmp.ohos_ffi.generator.type.collections.ArrayHandler
import com.bytedance.kmp.ohos_ffi.generator.type.collections.ByteArrayHandler
import com.bytedance.kmp.ohos_ffi.generator.type.collections.ListHandler
import com.bytedance.kmp.ohos_ffi.generator.type.collections.MapHandler
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability

object TypeManager {

    private val handlers = mutableListOf(
        // 基础类型
        BooleanHandler, IntHandler, LongHandler, DoubleHandler, FloatHandler, StringHandler, UnitHandler, BigIntHandler,
        // 枚举类型
        EnumTypeHandler,
        // 集合类型
        MapHandler, ByteArrayHandler, ArrayHandler, ListHandler,
        // 原始 napi_value
        NapiValueVarHandler, NapiValueHandler,
        // 自定义类型
        CustomTypeHandler
    )

    private fun getHandler(type: KSType): TypeHandler? {
        return handlers.firstOrNull {
            it.match(type)
        }
    }

    /**
     * 检查是否为内置支持的类型
     */
    fun isSupportClass(type: KSType): Boolean {
        return getHandler(type) != null
    }

    fun matchType(type: KSType, vararg typeEnum: TypeEnum): Boolean {
        val handler = getHandler(type)
        return handler != null && handler.type in typeEnum
    }

    /**
     * 生成将 js 对象转换为 Kotlin 对象的 Kotlin 代码模板
     */
    fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean = false): String {
        val handler = getHandler(type) ?: error("can not handle ${type.declaration.qualifiedName?.asString()}")
        var result = handler.jsObj2KotlinObjCode(kotlinCode, type, nullable)
        if (!nullable && !result.endsWith("!!")) {
            result += "!!"
        }
        return result
    }

    /**
     * 生成将 kotlin 对象转换为 js 对象的 Kotlin 代码模板
     */
    fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean = false): String {
        val handler = getHandler(type) ?: error("can not handle ${type.declaration.qualifiedName?.asString()}")
        return handler.kotlinObj2JsObjCode(kotlinCode, type, nullable)
    }

    fun jsTypeStr(type: KSType): String {
        val handler = getHandler(type) ?: error("can not handle ${type.declaration.qualifiedName?.asString()}")
        return handler.jsTypeStr(type)
    }

    fun kotlinTypeStr(type: KSType): String? {
        val handler = getHandler(type) ?: error("can not handle ${type.declaration.qualifiedName?.asString()}")
        var typeStr = handler.kotlinTypeStr(type)
        if (type.nullable()) {
            typeStr += "?"
        }
        return typeStr
    }
}

enum class TypeEnum {
    PRIMITIVE, ENUM_CLASS, COLLECTION, CUSTOM_CLASS, NAPI_VALUE
}

abstract class TypeHandler {

    abstract val type: TypeEnum

    abstract fun match(type: KSType): Boolean

    /**
     * 生成将 js 对象转换为 Kotlin 对象的 Kotlin 代码模板
     */
    abstract fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String

    /**
     * 生成将 kotlin 对象转换为 js 对象的 Kotlin 代码模板
     */
    abstract fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String

    /**
     * 获取对应的 js type，用于生成Index.d.ts
     */
    abstract fun jsTypeStr(type: KSType): String

    /**
     * 获取对应的 kotlin type，用于生成Kotlin代码
     */
    abstract fun kotlinTypeStr(type: KSType): String
}
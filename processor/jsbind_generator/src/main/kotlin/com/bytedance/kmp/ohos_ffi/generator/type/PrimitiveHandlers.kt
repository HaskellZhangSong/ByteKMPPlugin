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

import com.google.devtools.ksp.symbol.KSType

abstract class PrimitiveTypeHandler: TypeHandler() {

    override val type = TypeEnum.PRIMITIVE

    abstract val kotlinType: String

    override fun match(type: KSType): Boolean {
        return kotlinType == type.declaration.qualifiedName?.asString()!!
    }

    override fun kotlinTypeStr(type: KSType): String {
        return kotlinType.split(".").last()
    }

}

object BooleanHandler: PrimitiveTypeHandler() {
    override val kotlinType: String = "kotlin.Boolean"

    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean) = "$kotlinCode.asBoolean()"

    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        return "createBoolean($kotlinCode)"
    }

    override fun jsTypeStr(type: KSType) = "boolean"
}

object IntHandler: PrimitiveTypeHandler() {
    override val kotlinType: String = "kotlin.Int"

    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean) = "$kotlinCode.asInt()"

    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        return "createInt($kotlinCode)"
    }

    override fun jsTypeStr(type: KSType) = "number"
}

object LongHandler: PrimitiveTypeHandler() {
    override val kotlinType: String = "kotlin.Long"

    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean) = "$kotlinCode.asLong()"

    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        return "createLong($kotlinCode)"
    }

    override fun jsTypeStr(type: KSType) = "number"
}

object DoubleHandler: PrimitiveTypeHandler() {
    override val kotlinType: String = "kotlin.Double"

    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean) = "$kotlinCode.asDouble()"

    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        return "createDouble($kotlinCode)"
    }

    override fun jsTypeStr(type: KSType) = "number"
}

object FloatHandler: PrimitiveTypeHandler() {
    override val kotlinType: String = "kotlin.Float"

    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean) = "$kotlinCode.asDouble().toFloat()"

    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        return "createDouble($kotlinCode.toDouble())"
    }

    override fun jsTypeStr(type: KSType) = "number"
}

object StringHandler: PrimitiveTypeHandler() {
    override val kotlinType: String = "kotlin.String"

    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean) = "$kotlinCode.asString()"

    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        return "createString($kotlinCode)"
    }

    override fun jsTypeStr(type: KSType) = "string"
}

object UnitHandler: PrimitiveTypeHandler() {
    override val kotlinType: String = "kotlin.Unit"

    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean) = error("cannot convert js object to kotlin.Unit")

    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean) = "null"

    override fun jsTypeStr(type: KSType) = "void"
}

object BigIntHandler: PrimitiveTypeHandler() {
    override val kotlinType: String = "com.bytedance.kmp.ohos_ffi.types.BigInt"
    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean) = "$kotlinCode.asBigInt()"
    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        return "createBigInt($kotlinCode)"
    }
    override fun jsTypeStr(type: KSType) = "BigInt"
}
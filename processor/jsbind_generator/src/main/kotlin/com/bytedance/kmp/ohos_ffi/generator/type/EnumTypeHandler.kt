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

import com.bytedance.kmp.ohos_ffi.generator.js_bind.BaseJsBindGenerator
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

object EnumTypeHandler: TypeHandler() {
    override val type: TypeEnum = TypeEnum.ENUM_CLASS

    override fun match(type: KSType): Boolean {
        return (type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS
    }

    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        return "${type.declaration.qualifiedName!!.asString()}.values()[$kotlinCode.asInt()]"
    }

    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        return "createInt($kotlinCode.ordinal)"
    }

    override fun jsTypeStr(type: KSType): String {
        val declaration = type.declaration
        return BaseJsBindGenerator.getCustomName(declaration) ?: declaration.simpleName.asString()
    }

    override fun kotlinTypeStr(type: KSType): String {
        return type.declaration.simpleName.asString()
    }
}
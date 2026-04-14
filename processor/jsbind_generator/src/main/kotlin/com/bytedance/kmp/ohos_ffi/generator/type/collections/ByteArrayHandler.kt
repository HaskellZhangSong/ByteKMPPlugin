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
import com.google.devtools.ksp.symbol.KSType

object ByteArrayHandler: TypeHandler() {

    override val type = TypeEnum.COLLECTION
    override fun match(type: KSType): Boolean {
        val typeName = type.declaration.qualifiedName?.asString()!!
        return typeName == "kotlin.ByteArray"
    }

    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        return "$kotlinCode.fromArrayBuffer()"
    }

    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        val result = "createArrayBuffer($kotlinCode)"
        return result
    }

    override fun jsTypeStr(type: KSType) = "ArrayBuffer"
    override fun kotlinTypeStr(type: KSType): String {
        return "ByteArray"
    }

}
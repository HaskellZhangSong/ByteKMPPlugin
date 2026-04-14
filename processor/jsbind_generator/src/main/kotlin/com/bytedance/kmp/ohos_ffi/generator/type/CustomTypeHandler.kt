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
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindClassGenerator
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.customtransform.CustomTransformManager
import com.google.devtools.ksp.symbol.KSType

object CustomTypeHandler: TypeHandler() {

    override val type = TypeEnum.CUSTOM_CLASS

    override fun match(type: KSType): Boolean {
        return BaseJsBindGenerator.isCustomClassType(type)
    }

    override fun jsObj2KotlinObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        // 自定义类型直接使用 ksp 生成的获取实例代码
        val className = type.declaration.simpleName.asString()
        return "$kotlinCode.${JsBindClassGenerator.getKotlinInstanceMethodName(className)}()"
    }

    override fun kotlinObj2JsObjCode(kotlinCode: String, type: KSType, nullable: Boolean): String {
        val dot = if (nullable) "?." else "."
        // 自定义类型直接使用 ksp 生成的获取实例代码
        val result = "$kotlinCode$dot${JsBindClassGenerator.CREATE_JS_OBJ_METHOD_NAME}()${dot}napiValue"
        return result
    }

    override fun jsTypeStr(type: KSType): String {
        val declaration = type.declaration
        if (CustomTransformManager.useCustomTransformer(declaration)) {
            return "ESObject"
        }
        return BaseJsBindGenerator.getCustomName(declaration) ?: declaration.simpleName.asString()
    }

    override fun kotlinTypeStr(type: KSType): String {
        return type.declaration.simpleName.asString()
    }
}
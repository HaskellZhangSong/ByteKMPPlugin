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

package com.bytedance.kmp.ohos_ffi.generator.js_bind.export.customtransform

import com.bytedance.kmp.ohos_ffi.generator.js_bind.BaseJsBindGenerator.Companion.ANNOTATION_CLASS
import com.bytedance.kmp.ohos_ffi.generator.js_bind.BaseJsBindGenerator.Companion.getOutputName
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindClassGenerator.Companion.ANNOTATION_CUSTOM_TRANSFORM
import com.bytedance.kmp.ohos_ffi.generator.js_bind.export.JsBindClassGenerator.Companion.CUSTOM_TRANSFORMER_INTERFACE
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType

object CustomTransformManager {

    private var hasInit = false
    private val customTransformers = mutableListOf<CustomTransformer>()

    fun init(resolver: Resolver) {
        if (hasInit) {
            return
        }
        hasInit = true
        collectCustomTransformer(resolver)
    }

    private fun collectCustomTransformer(resolver: Resolver) {
        resolver.getSymbolsWithAnnotation(ANNOTATION_CUSTOM_TRANSFORM).forEach {
            val anno = it.annotations.firstOrNull {
                it.annotationType.resolve().declaration
                    .qualifiedName?.asString() == ANNOTATION_CUSTOM_TRANSFORM
            }!!
            val targetClass = anno.arguments.firstOrNull {
                it.name?.asString() == "clazz"
            }?.value as KSType

            val transformer = it as KSClassDeclaration
            // 检查Transformer 是否 Object 类型
            if (transformer.classKind != ClassKind.OBJECT) {
                throw RuntimeException("CustomTransformer must be a Object Class, ${getOutputName(transformer)}")
            }
            // 检查 Transformer 是否实现了接口
            val interfaceType = transformer.getAllSuperTypes().firstOrNull { it.declaration.qualifiedName?.asString() == CUSTOM_TRANSFORMER_INTERFACE }
            if (interfaceType == null) {
                throw RuntimeException("CustomTransformer must implement $CUSTOM_TRANSFORMER_INTERFACE, ${getOutputName(transformer)}")
            }
//            // 检查接口的泛型是否和 targetClass 一致
//            val genericType = interfaceType.arguments.first().type?.resolve()?.declaration
//            if (genericType?.qualifiedName?.asString()
//                != targetClass.declaration.qualifiedName?.asString()) {
//                throw RuntimeException("CustomTransformer generic type must be ${targetClass.declaration.qualifiedName?.asString()}, ${getOutputName(transformer)}")
//            }
            customTransformers.add(CustomTransformer(targetClass.declaration.qualifiedName?.asString()!!, transformer.qualifiedName?.asString()!!))
        }
    }

    fun getTransformer(targetClass: String): CustomTransformer? {
        return customTransformers.firstOrNull { it.targetClass == targetClass }
    }

    fun useCustomTransformer(clazz: KSDeclaration): Boolean {
        val annotation = clazz.annotations.firstOrNull {
            it.annotationType.resolve().declaration
                .qualifiedName?.asString() == ANNOTATION_CLASS
        } ?: return false
        return annotation.arguments.firstOrNull { it.name?.asString() == "customTransform" }?.value as? Boolean
            ?: false
    }

    fun clear() {
        hasInit = false
        customTransformers.clear()
    }
}

data class CustomTransformer(val targetClass: String, val transformer: String)
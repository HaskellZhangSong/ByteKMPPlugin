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
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability

/**
 * 检查是否顶层声明，包括companion object & Object
 */
fun checkTopLevel(declaration: KSDeclaration) {
    val parent = declaration.parent
    if (parent is KSFile) {
        return
    }
    if (parent is KSClassDeclaration
        && (parent.isCompanionObject || parent.classKind == ClassKind.OBJECT)) {
        return
    }
    throw RuntimeException("${BaseJsBindGenerator.getOutputName(declaration)} is not a top level declaration")
}

/**
 * 检查是否声明为 public
 */
fun checkPublic(declaration: KSDeclaration) {
    if (!declaration.isPublic()) {
        throw RuntimeException("${BaseJsBindGenerator.getOutputName(declaration)} is not public")
    }
}

/**
 * 检查是否为基础类型
 */
fun isPrimitive(declaration: KSDeclaration): Boolean {
    return declaration.qualifiedName?.asString() in listOf(
        "kotlin.Int",
        "kotlin.Long",
        "kotlin.Double",
        "kotlin.Float",
        "kotlin.Boolean",
        "kotlin.String",
        "kotlin.Unit"
    )
}

/**
 * 检查是否为支持的集合类型
 */
fun isCollectionsType(declaration: KSDeclaration): Boolean {
    return declaration.qualifiedName?.asString() in listOf(
        "kotlin.ByteArray",
        "kotlin.collections.Map"
    )
}

fun isSuspend(function: KSFunctionDeclaration): Boolean {
    return function.modifiers.any { it == Modifier.SUSPEND }
}

fun getSimpleName(type: KSType?): String? {
    return type?.declaration?.simpleName?.asString()
}

fun KSType.nullable(): Boolean {
    return this.nullability == Nullability.NULLABLE
}

fun KSDeclaration.getAnnotation(anno: String): KSAnnotation? {
    return annotations.firstOrNull {
        it.annotationType.resolve().declaration
            .qualifiedName?.asString() == anno
    }
}
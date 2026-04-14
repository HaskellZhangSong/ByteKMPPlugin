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

@file:OptIn(KspExperimental::class)

package com.bytedance.ksp.metainfo

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object KspMetaInfoManager {

    const val META_INFO_ANNOTATION = "com.bytedance.ksp.metainfo.CommonKspMetaInfo"
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    inline fun <reified T> saveMetaInfo(environment: SymbolProcessorEnvironment, packageName: String, fileName: String, key: String, content: T) {
        val file =
            environment.codeGenerator.createNewFile(Dependencies.ALL_FILES, packageName, fileName)
        file.writer().use {
            it.write(
                """
                    package $packageName
                    import $META_INFO_ANNOTATION
                    
                    @CommonKspMetaInfo("${key}", "${json.encodeToString(content).replace("\"", "\\\"")}")
                    private val a = 1
                """.trimIndent()
            )
        }
    }

    inline fun <reified T> getAllMetaInfo(resolver: Resolver, packageName: String, key: String): List<T> {
        return resolver.getDeclarationsFromPackage(packageName).mapNotNull {
            val annotation = it.annotations.firstOrNull {anno ->
                anno.annotationType.resolve().declaration.qualifiedName?.asString() ==  META_INFO_ANNOTATION
            } ?: return@mapNotNull null
            val realKey = annotation.arguments.firstOrNull {
                it.name?.asString() == "key"
            }?.value as String
            if (realKey != key) {
                return@mapNotNull null
            }
            val content = annotation.arguments.firstOrNull {
                it.name?.asString() == "content"
            }?.value as String
            json.decodeFromString<T>(content)
        }.toList()
    }
}
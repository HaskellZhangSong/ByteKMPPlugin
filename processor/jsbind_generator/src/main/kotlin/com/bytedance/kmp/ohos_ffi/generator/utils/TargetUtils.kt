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

package com.bytedance.kmp.ohos_ffi.generator.utils

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import java.io.File

fun getKspTargetName(environment: SymbolProcessorEnvironment): String {
    return environment.codeGenerator.run {
        val kotlinDir = javaClass.getDeclaredField("kotlinDir").apply {
            isAccessible = true
        }.get(this) as File
        val projectBase = javaClass.getDeclaredField("projectBase").apply {
            isAccessible = true
        }.get(this) as File
        kotlinDir.absolutePath.removePrefix(projectBase.absolutePath).removePrefix("/build/generated/ksp").split("/")[1]
    }.apply { environment.logger.warn("ksp target $this") }
}
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

package com.bytedance.kmp.ohos_ffi.har_bundle.utils

import org.apache.tools.ant.Project
import java.io.File

fun createNewDir(parent: File, path: String): File {
    return File(parent, path).apply {
        if (exists()) {
            deleteRecursively()
        }
        mkdirs()
    }
}

fun createNewDir(file: File) : File {
    return file.apply {
        if(file.exists()) {
            file.deleteRecursively()
        }
        mkdirs()
    }
}

fun createNewFile(parent: File, name: String): File {
    return File(parent, name).apply {
        if (exists()) {
            deleteRecursively()
        } else if (!parentFile.exists()) {
            parentFile.mkdirs()
        }
        createNewFile()
    }
}

fun renameDirectory(origin: File, to: File): File {
    val target = createNewDir(to)
    origin.copyRecursively(to, true)
    origin.deleteRecursively()
    return to
}

fun renameDirectory(origin: File, newName: String): File {
    val targetFile = createNewDir(origin.parentFile, newName)
    origin.copyRecursively(targetFile, true)
    origin.deleteRecursively()
    return targetFile
}

fun File.rewrite(block: (String) -> String) {
    val newContent = block(this.readText())
    writeText(newContent)
}
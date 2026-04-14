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

package com.bytedance.kmp.ohos_ffi.har_bundle.processor.resources

import com.bytedance.kmp.ohos_ffi.har_bundle.utils.rewrite
import org.gradle.api.Project
import java.io.File

class ResourceRefItem(
    // media/string/strarray/plural
    val type: String,
    val name: String
)

fun Project.writeKMPResourceRef(
    resourceRefArktsFile: File,
    indexFile: File,
    resourcesRefItem: List<ResourceRefItem>
) {
    resourceRefArktsFile.deleteOnExit()
    resourceRefArktsFile.parentFile.mkdirs()
    resourceRefArktsFile.createNewFile()
    resourceRefArktsFile.writer().use { writer ->
        writer.write("export function KmpResourceRefs() {\n")
        resourcesRefItem.forEach {
            writer.write("\$r('app.${it.type}.${it.name}')\n")
        }

        writer.write("}\n")
    }

    indexFile.rewrite {
        "$it\nexport { KmpResourceRefs } from './src/main/ets/${resourceRefArktsFile.name}'"
    }
}
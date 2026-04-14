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

package com.bytedance.kotlin.multiplatform

import org.gradle.api.Project

internal const val PLUGIN_BUNDLE_HAR  = "com.bytedance.kmp.ohos.ffi:so_har_generator"
internal const val PLUGIN_KMP_PUBLISH = "com.bytedance.kmp.publish:publish"

internal const val EXTENSION_KLIB_PUBLICATION = "bytekmp_klib_publish"
internal const val EXTENSION_SO_HAR_GENERATOR = "soHarGenerator"

fun Project.applyIfAbsent(pluginId: String) {
    if(!this.plugins.hasPlugin(pluginId)) {
        this.plugins.apply(pluginId)
    }
}
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

package com.bytedance.kmp.ohos_ffi.har_bundle

// 兼容旧版本代码用
@Deprecated("Please use HarExtension instead", ReplaceWith("HarExtension", "com.bytedance.kmp.ohos_ffi.har_bundle"))
open class OhosFfiExtension {
    var soName: String? = null
    var harName: String? = null
    var harVersion: String? = null
    var rootModuleName: String? = "entry"
    @Deprecated("will be remove") var generateLocalHarOnly: Boolean = false
    var localHarPath: String? = null
    @Deprecated("will be remove") var linkSoList: List<String> = emptyList()
    @Deprecated("will be remove") var externalSoList: List<String> = emptyList()
    var excludeSoName: List<String> = emptyList()
    @Deprecated("will be remove") var buildMode: String = "debug" // debug or release
    @Deprecated("will be remove") var stripSymbol: Boolean? = null // 默认情况根据 buildMode 来决定，如果业务主动设置则以改配置为主
    @Deprecated("will be remove, use useSystemPathForHvigorw instead") var isCI: Boolean = false
    @Deprecated("will be remove") var templateDir: String ? = null
    var ideDir: String? = null
    var useSystemPathForHvigorw: Boolean = false
    var enableCompose: Boolean = true
    var enableOhosResourceCompress: Boolean = true

    var enablePerformanceProbe: Boolean = false

    fun check() {
        if (soName.isNullOrEmpty()) {
            error("[SoHarGeneratorPlugin] 缺少 soName 配置")
        }
        if (harName.isNullOrEmpty()) {
            error("[SoHarGeneratorPlugin] 缺少 harName 配置")
        }
        if (harVersion.isNullOrEmpty()) {
            error("[SoHarGeneratorPlugin] 缺少 harVersion 配置")
        }
        if (rootModuleName.isNullOrEmpty()) {
            error("[SoHarGeneratorPlugin] rootModuleName不能为空")
        }
        if (ideDir.isNullOrEmpty()) {
            ideDir = "/Applications/DevEco-Studio.app"
        }
        if (ideDir?.isNotEmpty() == true && ideDir?.endsWith("/") == true) {
            ideDir = ideDir?.removeSuffix("/")
        }
    }

}

open class HarExtension : OhosFfiExtension()
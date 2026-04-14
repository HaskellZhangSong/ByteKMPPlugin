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

import java.io.File

class BundleHarConfig {
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
    @Deprecated("will be remove") var useSystemPathForHvigorw: Boolean = false
    @Deprecated("will be remove") var templateDir: String ? = null
    var ideDir: String? = null
    var enableCompose: Boolean = true
    var enableOhosResourceCompress: Boolean = true

    var templateProjectDir : File? = null
    var templateCacheDir : File? = null

    var enablePerformanceProbe: Boolean = false

    fun copyForm(extension: HarExtension) : BundleHarConfig {
        this.soName = extension.soName
        this.harName = extension.harName
        this.harVersion = extension.harVersion
        this.rootModuleName = extension.rootModuleName
        this.generateLocalHarOnly = extension.generateLocalHarOnly
        this.localHarPath = extension.localHarPath
        this.linkSoList = extension.linkSoList
        this.externalSoList = extension.externalSoList
        this.excludeSoName = extension.excludeSoName
        this.buildMode = extension.buildMode
        this.stripSymbol = extension.stripSymbol
        this.useSystemPathForHvigorw = extension.isCI || extension.useSystemPathForHvigorw

        this.templateDir = extension.templateDir
        this.ideDir = extension.ideDir
        this.enableCompose = extension.enableCompose
        this.enableOhosResourceCompress = extension.enableOhosResourceCompress
        this.enablePerformanceProbe = extension.enablePerformanceProbe
        return this
    }
}

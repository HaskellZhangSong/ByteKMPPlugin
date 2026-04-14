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

@file:OptIn(ExperimentalForeignApi::class)

package com.bytedance.kmp.compose.ohos.performance

import androidx.compose.ui.util.PreComposeProbe
import androidx.compose.ui.window.ComposeController
import com.bytedance.kmp.compose.ohos.library.ProxyFrameHolderGetter
import com.bytedance.kmp.compose.ohos.library.ShellFrameImportApi
import com.bytedance.kmp.compose.ohos.library.ShellRenderView
import com.bytedance.kmp.compose.ohos.library.ShellSizeConstraint
import com.bytedance.kmp.compose.ohos.library.proxy
import com.bytedance.kmp.compose.ohos.library.setupRenderView
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportFunction
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ohos.napi.napi_value

@KotlinExportFunction
fun initRenderNodeWithPreloadProbe(
    type: String, importApi: ShellFrameImportApi, constraint: ShellSizeConstraint?, rootContent: napi_value?,
    param: napi_value, interopBottomNodeContent: napi_value, interopTopNodeContent: napi_value, textToolbar: napi_value?, nodeController: napi_value, hitTestMode: Int,
    isPreCompose: Boolean, preloadProbe: PreloadProbe?, frameNodeId: Int?
): ShellRenderView {
    val getter = ProxyFrameHolderGetter()
    return setupRenderView(
        getter, ComposeController.initRenderNode(
            type, rootContent, importApi.proxy, param, interopBottomNodeContent, interopTopNodeContent, textToolbar, nodeController, getter,
            constraint, hitTestMode, isPreCompose, preloadProbe?.let { PreComposeProbeImpl(it) },
            PreloadProbeComposeValueProvider(preloadProbe), frameNodeId = frameNodeId
        )
    )
}

private class PreComposeProbeImpl(private val delegate: PreloadProbe) : PreComposeProbe {
    override fun isActualLaunched(): Long = delegate.isActualLaunched()
}

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

package com.bytedance.kmp.compose.ohos.library

import androidx.compose.ui.window.ComposeController
import com.bytedance.kmp.harko.HarkoContext
import com.bytedance.kmp.harko.HarkoContext.ArkExportInterface
import com.bytedance.kmp.harko.OHLogger
import com.bytedance.kmp.harko.URLHandler
import com.bytedance.kmp.harko.render.FrameRenderView
import com.bytedance.kmp.harko.skia.Rect
import com.bytedance.kmp.ohos_ffi.annotation.ArkSoInitFunction
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExport
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportClass
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportFunction
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportInterface
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ohos.napi.napi_env
import platform.ohos.napi.napi_value

private const val TAG = "ComposeFFIEntrance"

@ArkSoInitFunction(0)
fun entrance(env: napi_env, exports: napi_value) {
    OHLogger.i(TAG, "entrance")
    HarkoContext.init(env)
}

@KotlinExportFunction
fun onForeground() {
    ArkExportInterface.onForeground()
}

@KotlinExportFunction
fun onBackground() {
    ArkExportInterface.onBackground()
}

@KotlinExportFunction
fun onDensityPixelsChanged(dpi: Double) {
    ArkExportInterface.onDpiChanged(dpi)
}

@KotlinExportFunction
fun setCApiFixed(fixed: Boolean) {
    ArkExportInterface.setCApiFixed(fixed)
}

@KotlinExportFunction
fun onThemeChanged(isDarkTheme: Boolean) {
    ArkExportInterface.onThemeChanged(isDarkTheme)
}

@KotlinExportFunction
fun onKeyboardHeightChanged(height: Int) {
    ArkExportInterface.onKeyboardHeightChanged(height)
}

@KotlinExportFunction
fun onFontSizeScaleChanged(scale: Double) {
    ArkExportInterface.onFontSizeScaleChanged(scale)
}

@KotlinExportFunction
fun onFontWeightScaleChanged(weight: Double) {
    ArkExportInterface.onFontWeightScaleChanged(weight)
}

@KotlinExportFunction
fun onSystemFontIdChanged(systemFontId: String) {
    ArkExportInterface.onSystemFontIdChanged(systemFontId)
}

@KotlinExportFunction
fun setResourceManager(jsResMgr: napi_value) {
    ArkExportInterface.setResourceManager(jsResMgr)
}

@KotlinExportFunction
fun onLanguageChanged(language: String, region: String) {
    ArkExportInterface.onLanguageChanged(language, region)
}

@KotlinExportFunction
fun on24HourFormatChanged(is24HourFormat: Boolean) {
    ArkExportInterface.on24HourFormatChanged(is24HourFormat)
}

@KotlinExportFunction
fun onWindowInsetsChanged(
    safeArea: WindowInsetsRect, displayCutout: WindowInsetsRect, systemGestures: WindowInsetsRect
) {
    ArkExportInterface.onWindowInsetsChanged(safeArea.toRect(), displayCutout.toRect(), systemGestures.toRect())
}

@KotlinExportClass
class WindowInsetsRect {
    @KotlinExport
    var left = 0

    @KotlinExport
    var top = 0

    @KotlinExport
    var right = 0

    @KotlinExport
    var bottom = 0

    fun toRect(): Rect {
        return Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }
}

@KotlinExportInterface
interface ShellUrlHandler : URLHandler

@KotlinExportFunction
fun setUrlHandler(handler: ShellUrlHandler) {
    ArkExportInterface.setUrlHandler(handler)
}

@KotlinExportFunction
fun initRenderNode(
    type: String, importApi: ShellFrameImportApi, constraint: ShellSizeConstraint?, rootContent: napi_value?,
    param: napi_value, interopBottomNodeContent: napi_value, interopTopNodeContent: napi_value, textToolbar: napi_value?, nodeController: napi_value, hitTestMode: Int, isPreCompose: Boolean, frameNodeId: Int?
): ShellRenderView {
    OHLogger.i(TAG, "initRenderNode: $type")
    val getter = ProxyFrameHolderGetter()
    return setupRenderView(
        getter, ComposeController.initRenderNode(
            type, rootContent, importApi.proxy, param, interopBottomNodeContent, interopTopNodeContent, textToolbar, nodeController, getter, constraint,
            hitTestMode, isPreCompose, frameNodeId = frameNodeId
        )
    )
}

fun setupRenderView(getter: ProxyFrameHolderGetter, renderView: FrameRenderView): ShellRenderView {
    val view = ShellRenderView(renderView)
    getter.holder = view
    return view
}

@KotlinExportFunction
fun setNodeConstructor(nodeConstructor: napi_value, statusModifyConstructor: napi_value) {
    ArkExportInterface.setNodeConstructor(nodeConstructor, statusModifyConstructor)
}

@KotlinExportFunction
fun onJsNodeDraw(drawContext: napi_value, node: napi_value) {
    ArkExportInterface.onJsNodeDraw(drawContext, node)
}

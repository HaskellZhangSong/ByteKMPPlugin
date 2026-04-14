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

package com.bytedance.kmp.compose.ohos.library

import androidx.compose.ui.window.FrameHolderGetter
import com.bytedance.kmp.harko.OHLogger
import com.bytedance.kmp.harko.render.FrameExportApi
import com.bytedance.kmp.harko.render.FrameImportApi
import com.bytedance.kmp.harko.render.FrameRenderView
import com.bytedance.kmp.harko.render.RenderViewExportApi
import com.bytedance.kmp.harko.render.SizeConstraint
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExport
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportClass
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportInterface
import platform.ohos.napi.napi_value

private const val TAG = "ShellRenderView"

@KotlinExportClass(noConstructor = true)
class ShellRenderView(private val delegate: FrameRenderView) : RenderViewExportApi, ShellFrameExportApi {

    @KotlinExport
    override fun onSizeChanged(width: Int, height: Int, posChanged: Boolean) =
        delegate.onSizeChanged(width, height, posChanged)

    @KotlinExport
    override fun measureWidth(): Int = delegate.measureWidth()

    @KotlinExport
    override fun measureHeight(): Int = delegate.measureHeight()

    @KotlinExport
    override fun onShow() = delegate.onShow()

    @KotlinExport
    override fun onHide() = delegate.onHide()

    @KotlinExport
    override fun onAppear() = delegate.onAppear()

    @KotlinExport
    override fun onDestroy() {
        OHLogger.d(TAG, "onDestroy")
        delegate.onDestroy()
    }

    @KotlinExport
    override fun onTouchChange(id: Long, x: Int, y: Int, pressure: Float) = delegate.onTouchChange(id, x, y, pressure)

    @KotlinExport
    override fun onTouchEvent(type: Int, timestamp: Long, changeCnt: Int): Boolean =
        delegate.onTouchEvent(type, timestamp, changeCnt)

    @KotlinExport
    override fun onTouchIntercept(x: Float, y: Float): Int {
        return delegate.onTouchIntercept(x, y)
    }

    @KotlinExport
    override fun onBackPressed(): Boolean = delegate.onBackPressed()

    @KotlinExport
    override fun draw(drawContext: napi_value) = delegate.draw(drawContext)

    @KotlinExport
    override fun getJsNode(): napi_value? = delegate.getJsNode()

    @KotlinExport
    override fun onFrame(frameTime: Long) = delegate.onFrame(frameTime)

    @KotlinExport
    override fun onIdle(timeLeft: Long) = delegate.onIdle(timeLeft)

    @KotlinExport
    override fun onFocus() = delegate.onFocus()

    @KotlinExport
    override fun onBlur() = delegate.onBlur()

    @KotlinExport
    override fun updateTextToolbar(textToolbar: napi_value) = delegate.updateTextToolbar(textToolbar)

    @KotlinExport
    override fun updateFrameNodeId(frameNodeId: Int) = delegate.updateFrameNodeId(frameNodeId)
}

@KotlinExportClass(noConstructor = true)
interface ShellFrameExportApi : FrameExportApi {
    @KotlinExport
    override fun onFrame(frameTime: Long)

    @KotlinExport
    override fun onIdle(timeLeft: Long)
}

@KotlinExportInterface
interface ShellFrameImportApi {
    fun onFrame(view: ShellFrameExportApi)
    fun setHighRefreshRate(id: Long, enable: Boolean)
    fun resetSize(width: Int, height: Int)
}

val ShellFrameImportApi.proxy: FrameImportApi
    get() = ProxyFrameImportApi(this)

internal class ProxyFrameImportApi(private var importApi: ShellFrameImportApi?) : FrameImportApi {
    override fun postFrame(export: FrameExportApi) {
        importApi?.onFrame(export as ShellFrameExportApi)
    }

    override fun setHighRefreshRate(id: Long, enable: Boolean) {
        importApi?.setHighRefreshRate(id, enable)
    }

    override fun resetSize(width: Int, height: Int) {
        importApi?.resetSize(width, height)
    }

    override fun onDestroy() {
        OHLogger.d(TAG, "ProxyFrameImportApi onDestroy")
        importApi = null
    }
}

class ProxyFrameHolderGetter : FrameHolderGetter {
    internal var holder: ShellFrameExportApi? = null
    override fun invoke(): ShellFrameExportApi = holder ?: error("Should set holder first!!")
}

@KotlinExportInterface
interface ShellSizeConstraint : SizeConstraint {
    override fun width(): Int
    override fun height(): Int
}

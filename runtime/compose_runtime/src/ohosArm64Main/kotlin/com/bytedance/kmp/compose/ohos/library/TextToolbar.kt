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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.OhosLocalTextToolbar
import com.bytedance.kmp.compose.ohos.library.js_bind_import_interface.getITextToolBar
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExport
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportClass
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportInterface
import kotlinx.cinterop.ExperimentalForeignApi

@KotlinExportClass
class TextToolBarCallback {

    var onCopyRequested: (() -> Unit)? = null
    var onPasteRequested: (() -> Unit)? = null
    var onPasteResult: ((String) -> Unit)? = null
    var onCutRequested: (() -> Unit)? = null
    var onSelectAllRequested: (() -> Unit)? = null

    @KotlinExport
    fun onCopyRequested() {
        onCopyRequested?.invoke()
    }

    @KotlinExport
    fun onPasteRequested(content: String?) {
        if (content.isNullOrEmpty()) {
            onPasteRequested?.invoke()
        } else {
            onPasteResult?.invoke(content)
        }
    }

    @KotlinExport
    fun onCutRequested() {
        onCutRequested?.invoke()
    }

    @KotlinExport
    fun onSelectAllRequested() {
        onSelectAllRequested?.invoke()
    }
}

@KotlinExportInterface
interface ITextToolBar {
    fun updateToolbar(showToolbar: Boolean,
                      showCopy: Boolean, showPaste: Boolean, showCut: Boolean, showSelectAll: Boolean,
                      left: Int, top: Int, right: Int, bottom: Int,
                      callback: TextToolBarCallback)
}

@Composable
fun TextToolbar(width: State<Int>, height: State<Int>) {
    val textToolbar = OhosLocalTextToolbar.current
    val actionCallback = remember { TextToolBarCallback() }

    LaunchedEffect(textToolbar) {

        actionCallback.onCopyRequested = textToolbar.onCopyRequested
        actionCallback.onPasteRequested = textToolbar.onPasteRequested
        actionCallback.onPasteResult = textToolbar.onPasteResult
        actionCallback.onCutRequested = textToolbar.onCutRequested
        actionCallback.onSelectAllRequested = textToolbar.onSelectAllRequested

        // 需要判断输入框是否在 UI 内显示，如果划出了 UI 外则不显示
        val shouldShow = textToolbar.isShow &&
                textToolbar.rect.left <= width.value &&
                textToolbar.rect.right >= 0 &&
                textToolbar.rect.bottom >= 0 &&
                textToolbar.rect.top <= height.value

        println("[OhosTextToolbar] textToolbar update: shouldShow: $shouldShow, rect: ${textToolbar.rect}, width: $width, height: $height")

        // 获取到 ArkTs 传入的对象，控制 popup 显示
        textToolbar.arkTsTextToolBar?.invoke()?.getITextToolBar()?.updateToolbar(
            shouldShow,
            textToolbar.onCopyRequested != null,
            textToolbar.onPasteRequested != null || textToolbar.onPasteResult != null,
            textToolbar.onCutRequested != null,
            textToolbar.onSelectAllRequested != null,
            textToolbar.rect.left.toInt(), textToolbar.rect.top.toInt(),
            textToolbar.rect.right.toInt(), textToolbar.rect.bottom.toInt(),
            actionCallback
        )
    }
}

@Composable
fun withTextToolbar(content: @Composable () -> Unit) {
    var width = remember { mutableStateOf(0) }
    var height = remember { mutableStateOf(0) }

    Box(Modifier.onGloballyPositioned {
        width.value = it.size.width
        height.value = it.size.height
    }) {
        content()
        TextToolbar(width, height)
    }
}
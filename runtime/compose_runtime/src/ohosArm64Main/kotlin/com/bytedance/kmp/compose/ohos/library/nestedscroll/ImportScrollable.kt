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

package com.bytedance.kmp.compose.ohos.library.nestedscroll

import androidx.compose.foundation.gestures.NestedScrollOnTouchListener
import androidx.compose.ui.input.nestedscroll.InteropNestedScrollParam
import androidx.compose.ui.input.nestedscroll.ScrollDirection
import androidx.compose.ui.input.nestedscroll.Scrollable
import com.bytedance.kmp.harko.ark.NApiValue
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExport
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportClass
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportClassGenerator
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportInterface
import com.bytedance.kmp.ohos_ffi.types.ArkInstance
import platform.ohos.napi.napi_value

@KotlinExportInterface
interface ImportScrollable : Scrollable

@KotlinExportInterface
interface ImportOnTouchListener : NestedScrollOnTouchListener

@KotlinExportClass
open class NestedScrollParam @KotlinExportClassGenerator constructor(
    scrollable: ImportScrollable, val listener: ImportOnTouchListener?
) : InteropNestedScrollParam(ScrollDirection.VERTICAL, scrollable, listener != null) {
    private var componentContent: ArkInstance? = null
    override val componentContentValue: NApiValue
        get() = componentContent?.getNapiValue() ?: error("should call setComponentContent first")

    @KotlinExport
    fun setComponentContent(value: napi_value) {
        componentContent = ArkInstance(value)
    }

    @KotlinExport
    override fun onReachStart() {
        super.onReachStart()
    }

    @KotlinExport
    override fun onReachEnd() {
        super.onReachEnd()
    }

    @KotlinExport
    override fun onDidScroll(offset: Int) {
        super.onDidScroll(offset)
    }
}

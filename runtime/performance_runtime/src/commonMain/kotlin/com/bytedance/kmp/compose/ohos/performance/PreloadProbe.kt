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

package com.bytedance.kmp.compose.ohos.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.bytedance.kmp.ohos_ffi.annotation.KotlinExportInterface

@KotlinExportInterface
interface PreloadProbe {
    fun isActualLaunched(): Long
}

val PreloadProbeLocal = staticCompositionLocalOf<PreloadProbe?> {
    null
}

val preloadProbe: PreloadProbe?
    @Composable
    get() = PreloadProbeLocal.current

class PreloadProbeComposeValueProvider(private val probe: PreloadProbe?) : () -> Array<ProvidedValue<*>> {
    override fun invoke(): Array<ProvidedValue<*>> {
        probe ?: return emptyArray()
        return arrayOf(PreloadProbeLocal provides probe)
    }
}

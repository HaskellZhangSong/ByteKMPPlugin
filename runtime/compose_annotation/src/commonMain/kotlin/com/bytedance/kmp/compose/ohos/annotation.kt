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

package com.bytedance.kmp.compose.ohos

import kotlin.reflect.KClass

/**
 * 标注导出到 ArkTs 的@Composable 方法
 * id: ArkTs侧 XComponents 使用的 id，默认使用包名+方法名
 */
enum class HitTestMode {
    ARKUI_HIT_TEST_MODE_DEFAULT,
    ARKUI_HIT_TEST_MODE_BLOCK,
    ARKUI_HIT_TEST_MODE_TRANSPARENT,
    ARKUI_HIT_TEST_MODE_NONE
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ArkTsExportComposable(
    val id: String = "",
    val hitTestMode: HitTestMode = HitTestMode.ARKUI_HIT_TEST_MODE_DEFAULT,
    val touchInterceptor: String = "",
    val withInterop: Boolean = false,
    val needBackground: Boolean = true,
    val disableRecycleNode: Boolean = false,
    val withOffscreenRender: Boolean = false,
)
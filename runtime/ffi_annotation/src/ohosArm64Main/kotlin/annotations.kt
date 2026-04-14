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

package com.bytedance.kmp.ohos_ffi.annotation

import kotlin.reflect.KClass


/**
 * 标注 So init 方法，要求方法签名必须是👇这样
 * fun init(env: napi_env, exports: napi_value) {
 *     // ...
 * }
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ArkSoInitFunction(val priority: Int = Int.MAX_VALUE)

/**
 * 用于为 @KotlinExportClass 提供自定义的类型转换逻辑，同时也不会在 ArkTs 侧导出该对象
 * 要求当前类为 Object 且 实现了 ArkTsExportCustomTransformer 接口，泛型 T 与 clazz 一致
 * clazz: 目标类型，必须标注为 @KotlinExportClass，且 clazz 必须与当前 Transformer 类在同一组件
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ArkTsExportCustomTransform(val clazz: KClass<*>)
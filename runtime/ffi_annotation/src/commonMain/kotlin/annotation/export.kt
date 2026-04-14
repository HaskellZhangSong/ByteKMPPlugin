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

/**
 * 标注导出的顶层公开属性，包括 Object 和 companion object下的属性
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class KotlinExportProperty

/**
 * 标注导出的顶层公开方法，包括 Object 和 companion object下的方法
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KotlinExportFunction

/**
 * 标注导出的Class
 * singletonName: 若指定该字段则会在 ArkTs 侧生成当前类的单例对象并导出
 * noConstructor: 当前对象没有构造的方法(比如接口)，不允许 ArkTs 侧直接创建，只允许 Kotlin 侧创建
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KotlinExportClass(val singletonName: String = "", val noConstructor: Boolean = false, val customTransform: Boolean = false)

/**
 * 标注导出的 Enum Class
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KotlinExportEnum

/**
 * 在导出Class的伴生对象中标注获取实例的方法
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
annotation class KotlinExportClassGenerator

/**
 * 标注导出的Interface，用于 ArkTs 侧对象传递到 KN 侧使用，默认导出所有方法与字段
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class KotlinExportInterface

/**
 * 标注导出方法为线程安全，框架会保证在主线程调用
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KotlinThreadSafe

/**
 * 标注 Class 中导出的属性与方法
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class KotlinExport

/**
 * 自定义导出名称
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class KotlinExportName(val name: String)
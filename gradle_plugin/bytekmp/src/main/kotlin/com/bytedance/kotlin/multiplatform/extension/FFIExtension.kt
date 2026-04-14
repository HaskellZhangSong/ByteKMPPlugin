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

package com.bytedance.kotlin.multiplatform.extension

import com.android.build.api.dsl.ExternalNativeBuild
import com.android.build.gradle.BaseExtension
import com.bytedance.kotlin.multiplatform.Versions
import com.bytedance.kotlin.multiplatform.applyIfAbsent
import com.google.devtools.ksp.gradle.KspExtension
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.CInteropSettings
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File
import java.util.*

class FFIExtension(
    private val project: Project,
    private val kmpExtension: KotlinMultiplatformExtension,
    private val parent: ByteKMPExtension
) {

    private var _cinterop : Cinterop? = null
    val cinterop: Cinterop
        get() {
            var cinterop = _cinterop
            if (cinterop == null) {
                cinterop = Cinterop()
                _cinterop = cinterop
            }
            return cinterop
        }

    fun cinterop(action : Action<Cinterop>) {
        action.execute(cinterop)
    }

    fun cinterop(closure: Closure<*>) {
        project.configure(cinterop, closure)
    }

    internal var _arkTs : ArkTs? = null
    val arkTs: ArkTs
        get() {
            var arkTs = _arkTs
            if (arkTs == null) {
                arkTs = ArkTs()
                _arkTs = arkTs
            }
            return arkTs
        }

    fun arkTs(action : Action<ArkTs>) {
        action.execute(arkTs)
    }

    fun arkTs(closure: Closure<*>) {
        project.configure(arkTs, closure)
    }

    private var _jni : JNI? = null
    val jni: JNI
        get() {
            var jni = _jni
            if (jni == null) {
                jni = JNI()
                _jni = jni
            }
            return jni
        }

    fun jni(action : Action<JNI>) {
        action.execute(jni)
    }

    fun jni(closure : Closure<*>) {
        project.configure(jni, closure)
    }

//    private var _ios : IOS? = null
//
//    val ios: IOS
//        @Incubating get() {
//            var ios = _ios
//            if (ios == null) {
//                ios = IOS()
//                _ios = ios
//            }
//            return ios
//        }
//
//    @Incubating fun ios(action : Action<IOS>) {
//        action.execute(ios)
//    }
//
//    fun ios(closure: Closure<*>) {
//        project.configure(ios, closure)
//    }

    fun checkValid() {
    }

    private fun getKspExtension(): KspExtension {
        return project.extensions.findByName("ksp") as KspExtension
    }

    inner class Cinterop {

        fun createOhos(name: String = "main", action: Action<CInteropConfig>) {
            createOhosArm64(name, action)
        }

        fun createIos(name: String = "main", action: Action<CInteropConfig>) {
            createIosArm64(name, action)
            createIosX64(name, action)
            createIosSimulatorArm64(name, action)
        }

        fun createLinux(name: String = "main", action: Action<CInteropConfig>) {
            createLinuxX64(name, action)
            createLinuxArm64(name, action)
        }

        fun createAndroidNative(name: String = "main", action: Action<CInteropConfig>) {
            createAndroidNativeX64(name, action)
            createAndroidNativeArm64(name, action)
            createAndroidNativeArm32(name, action)
            createAndroidNativeX86(name, action)
        }

        fun createMacOs(name: String = "main", action: Action<CInteropConfig>) {
            createMacosArm64(name, action)
            createMacosX64(name, action)
        }

        fun createWatchOs(name: String = "main", action: Action<CInteropConfig>) {
            createWatchosX64(name, action)
            createWatchosArm32(name, action)
            createWatchosArm64(name, action)
            createWatchosSimulatorArm64(name, action)
        }

        fun createWindows(name: String = "main", action: Action<CInteropConfig>) {
            createMingwX64(name, action)
        }

        fun createTvOS(name: String = "main", action: Action<CInteropConfig>) {
            createTvosArm64(name, action)
            createTvosX64(name, action)
            createTvosSimulatorArm64(name, action)
        }

        fun createOhosArm64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.ohosArm64 {
                applyConfig(name, action)
            }
        }

        fun createIosArm64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.iosArm64 {
                applyConfig(name, action)
            }
        }

        fun createIosX64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.iosX64 {
                applyConfig(name, action)
            }
        }

        fun createIosSimulatorArm64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.iosSimulatorArm64 {
                applyConfig(name, action)
            }
        }

        fun createLinuxX64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.linuxX64 {
                applyConfig(name, action)
            }
        }

        fun createLinuxArm64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.linuxArm64 {
                applyConfig(name, action)
            }
        }

        fun createMingwX64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.mingwX64 {
                applyConfig(name, action)
            }
        }


        fun createWatchosX64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.watchosX64 {
                applyConfig(name, action)
            }
        }

        fun createWatchosArm64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.watchosArm64 {
                applyConfig(name, action)
            }
        }

        fun createWatchosArm32(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.watchosArm32 {
                applyConfig(name, action)
            }
        }

        fun createWatchosSimulatorArm64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.watchosSimulatorArm64 {
                applyConfig(name, action)
            }
        }

        fun createMacosX64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.macosX64 {
                applyConfig(name, action)
            }
        }

        fun createMacosArm64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.macosArm64 {
                applyConfig(name, action)
            }
        }


        fun createAndroidNativeX64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.androidNativeX64 {
                applyConfig(name, action)
            }
        }

        fun createAndroidNativeArm64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.androidNativeArm64 {
                applyConfig(name, action)
            }
        }

        fun createAndroidNativeArm32(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.androidNativeArm32 {
                applyConfig(name, action)
            }
        }

        fun createAndroidNativeX86(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.androidNativeX86 {
                applyConfig(name, action)
            }
        }

        fun createTvosX64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.tvosX64 {
                applyConfig(name, action)
            }
        }

        fun createTvosArm64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.tvosArm64 {
                applyConfig(name, action)
            }
        }

        fun createTvosSimulatorArm64(name: String = "main", action: Action<CInteropConfig>) {
            kmpExtension.tvosSimulatorArm64 {
                applyConfig(name, action)
            }
        }

        private fun KotlinNativeTarget.applyConfig(
            name: String,
            action: Action<CInteropConfig>
        ) {
            val compilation = compilations.getByName("main")
            compilation.cinterops.create(name) {
                val config = CInteropConfig(
                    name = name,
                    cinteropSettings = it,
                    compilation = compilation
                )
                action.execute(config)

            }
        }

    }
    open inner class ArkTs {

        init {
            project.applyIfAbsent("com.google.devtools.ksp")
            kmpExtension.apply {
                if (parent.isIOS) {
                    return@apply
                }
                sourceSets.commonMain.dependencies {
                    implementation("com.bytedance.kmp.ohos.ffi:annotation:${Versions.CURRENT_VERSION}")
                }
                sourceSets.ohosMain.dependencies {
                    implementation("com.bytedance.kmp.ohos.ffi:library:${Versions.CURRENT_VERSION}")
                }
            }
            project.dependencies.apply {
                if (parent.isIOS) {
                    return@apply
                }
                add("kspOhosArm64", "com.bytedance.kmp.ohos.ffi:jsbind_generator:${Versions.CURRENT_VERSION}")
            }
            parent._isRootModule.observe {
                applyIsRootModule(it)
            }
            parent._enabledCompose.observe {
                applyComposeEnabled(it)
            }
        }

        var packageOfGeneratedClass by xValue("") {
            getKspExtension().arg("ohos_ffi_module_package", it)
        }

        internal fun applyIsRootModule(isRootModule: Boolean) {
            getKspExtension().arg("ohos_ffi_root_module", isRootModule.toString())
        }

        internal fun applyComposeEnabled(enabled: Boolean) {
            if(enabled) {
                kmpExtension.apply {
                    sourceSets.commonMain.dependencies {
                        implementation("com.bytedance.kmp.compose.ohos:annotation:${Versions.CURRENT_VERSION}")
                    }
                    if (!parent.isIOS) {
                        sourceSets.ohosMain.dependencies {
                            implementation("com.bytedance.kmp.compose.ohos:library:${Versions.CURRENT_VERSION}")
                        }
                    }
                }
                project.dependencies.apply {
                    if (parent.isIOS) {
                        return@apply
                    }
                    add("kspOhosArm64", "com.bytedance.kmp.ohos.ffi:jsbind_generator:${Versions.CURRENT_VERSION}")
                    add("kspOhosArm64", "com.bytedance.kmp.compose.ohos:processor:${Versions.CURRENT_VERSION}")
                }
            }
            // 不会从 enabled 转为 disabled
        }

    }

//    @Incubating
//    inner class IOS {
//        val iosX64 = "iosX64"
//        val iosArm64 = "iosArm64"
//        val iosSimulatorArm64 = "iosSimulatorArm64"
//
//        var enabledTarget: List<String> by xValue(emptyList()) {
//            if (this.toTypedArray().contentEquals(it.toTypedArray())) return@xValue
//            if (this.isEmpty()) {
//                project.applyIfAbsent("com.google.devtools.ksp")
//                it.forEach { targetName ->
//                    project.dependencies.apply {
//                        add(
//                            "ksp" + targetName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
//                            "com.bytedance.kmp.ios.ffi:ios_ffi_generator:${Versions.IOS_PLUGIN_VERSION}"
//                        )
//                    }
//                }
//                parent._isRootModule.observe { applyIsRootModule(it) }
//            } else {
//                error("请勿重复设置 ffi.ios.enableTarget 的值")
//            }
//        }
//
//        fun enabledTarget(vararg target: String) {
//            this.enabledTarget = target.toList()
//        }
//
//        var enableDebugLog by xValue(false) {
//            getKspExtension().arg("ios_ffi_log_debug", it.toString())
//        }
//
//        internal fun applyIsRootModule(isRootModule: Boolean) {
//            getKspExtension().arg("ios_ffi_root_module", isRootModule.toString())
//        }
//    }

    inner class JNI {
        val externalNativeBuild: ExternalNativeBuild get() {
            if (project.extensions.findByName("android") == null) {
                error("未找到 AGP 插件, 请先执行 apply 'com.android.application' 或 'com.android.library'")
            }
            val extension = project.extensions.findByName("android") as BaseExtension
            return extension.externalNativeBuild
        }

        fun externalNativeBuild(action: Action<ExternalNativeBuild>) {
            action.execute(externalNativeBuild)
        }

        fun externalNativeBuild(closure: Closure<*>) {
            project.configure(externalNativeBuild, closure)
        }
    }

    class CInteropConfig(
        private val name: String,
        private val cinteropSettings: CInteropSettings,
        private val compilation: KotlinNativeCompilation
    ) {
        var defFile: File? by xValue {
            require(it != null) { "defFile must not be null" }
            cinteropSettings.defFile(it)
        }

        var includeDirs: List<File> by xValue(emptyList()) {
            cinteropSettings.includeDirs(it)
        }

        fun includeDirs(vararg files: File) {
            this.includeDirs += files
        }

        var includeLibrary: List<File> by xValue(emptyList()) { list ->
            compilation.compileTaskProvider.configure {
                it.compilerOptions.freeCompilerArgs.addAll(
                    "-include-binary=${list.joinToString(",") { it.canonicalPath }}",
                )
                // 为了让你替换新 so 时候能重新编译，不然会走缓存导致不生效
                it.inputs.files(list).withPropertyName("${name}ncludeBinary")
            }
        }

        fun includeLibrary(vararg files: File) {
            includeLibrary += files
        }
    }


}
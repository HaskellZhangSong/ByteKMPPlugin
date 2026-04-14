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

import com.bytedance.kmp.ohos_ffi.har_bundle.HarExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.SoHarGeneratorPlugin
import com.bytedance.kotlin.multiplatform.applyIfAbsent
import com.bytedance.kotlin.multiplatform.gradle.publish.KmpPublishingExtension
import com.bytedance.kotlin.multiplatform.gradle.publish.KmpPublishingExtension.Companion.KMP_EXTENSION_NAME
import com.bytedance.kotlin.multiplatform.gradle.publish.Publisher
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind
import org.jetbrains.kotlin.gradle.plugin.mpp.SharedLibrary
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

class PublishExtension(
    private val project: Project,
    private val kmpExtension: KotlinMultiplatformExtension,
    private val parentExtension: ByteKMPExtension
) {

    private var _klib: Klib? = null
    val klib: Klib
        get() {
            var klib = _klib
            if (klib == null) {
                klib = Klib()
                _klib = klib
            }
            return klib
        }

    fun klib(action: Action<in Klib>) {
        action.execute(klib)
    }

    fun klib(closure: Closure<*>) {
        project.configure(klib, closure)
    }

    private var _har: Har? = null
    val har: Har
        get() {
            var har = _har
            if (har == null) {
                har = Har()
                _har = har
            }
            return har
        }

    fun har(action: Action<in Har>) {
        action.execute(har)
    }

    fun har(closure: Closure<*>) {
        project.configure(har, closure)
    }

    private var _framework: Framework? = null
    val framework: Framework
        get() {
            var framework = _framework
            if (framework == null) {
                framework = Framework()
                _framework = framework
            }
            return framework
        }

    fun framework(action: Action<in Framework>) {
        action.execute(framework)
    }

    fun framework(closure: Closure<*>) {
        project.configure(framework, closure)
    }

    private var _xCFramework: XCFramework? = null
    val xCFramework: XCFramework
        get() {
            var xcFramework = _xCFramework
            if (xcFramework == null) {
                xcFramework = XCFramework()
                _xCFramework = xcFramework
            }
            return xcFramework
        }

    fun xCFramework(action: Action<in XCFramework>) {
        action.execute(xCFramework)
    }

    fun xCFramework(closure: Closure<*>) {
        project.configure(xCFramework, closure)
    }

//    private var _cocoaPods : CocoaPods? = null
//    val cocoaPods: CocoaPods
//        @Incubating get() {
//            var cocoaPods = _cocoaPods
//            if (cocoaPods == null) {
//                cocoaPods = CocoaPods()
//                _cocoaPods = cocoaPods
//            }
//            return cocoaPods
//        }
//
//    @Incubating fun cocoaPods(action: Action<CocoaPods>) {
//        action.execute(cocoaPods)
//    }
//
//    @Incubating fun cocoaPods(configure: Closure<*>) {
//        project.configure(cocoaPods, configure)
//    }

    inner class Klib {

        var enabled by xValue(false) {
            if (it) {
                Publisher.initForProject(project)
            }
            if (this && !it) { // true -> false
                error("请勿反复修改 publish.klib.enable 的值")
            }
        }

        var group by xValue("") {
            require(enabled) { "klib 未启用. 请先设置 publish.klib { enabled = true }. " }
            getKmpPublishingExtension().group = it
        }

        var version by xValue("") {
            require(enabled) { "klib 未启用. 请先设置 publish.klib { enabled = true }. " }
            getKmpPublishingExtension().version = it
        }

        var uploadSource by xValue(true) {
            require(enabled) { "klib 未启用. 请先设置 publish.klib { enabled = true }. " }
            kmpExtension.withSourcesJar(it)
        }

        private fun getKmpPublishingExtension(): KmpPublishingExtension {
            return project.extensions.findByName(KMP_EXTENSION_NAME) as KmpPublishingExtension
        }

        internal fun checkValid() {
            if (!enabled) return
            require(group.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! klib group 字段未设置，请参考右侧样例设置： publish.klib { group = \"xxx.xxxx.xxx\" }" }
            require(version.isNotBlank()) { "[${project.path} <- 该项目配置异常]!!klib version 字段未设置，请参考右侧样例设置： publish.klib { version = \"1.0.0\" }" }
        }
    }

    inner class Har {

        var enabled by xValue(false) {
            if (it) {
                project.applyIfAbsent("com.bytedance.kmp.ohos_ffi.so_har_generator")
                val devecoDir = project.buildProperties.get("deveco.dir")?.toString()
                if (devecoDir == "{SYSTEM_PATH}") {
                    getHarExtension().useSystemPathForHvigorw = true
                } else if (devecoDir != null) {
                    getHarExtension().ideDir = devecoDir.removeSuffix("/")
                    getHarExtension().useSystemPathForHvigorw = false
                } else {
                    val defaultDevecoDir = "/Applications/DevEco-Studio.app"
//                    if (!File(defaultDevecoDir).exists()) {
//                        error("未找到 deveco / hvigrow 位置，请在 local.properties 中设置 deveco.dir=你的deveco地址")
//                    }
                    getHarExtension().ideDir = defaultDevecoDir
                    getHarExtension().useSystemPathForHvigorw = false
                }
                parentExtension._enabledCompose.observe {
                    applyComposeEnabled(it)
                }
                parentExtension._enabledComposeResources.observe {
                    applyComposeResourcesEnabled(it)
                }
            }
            if (this && !it) { // true -> false
                error("请勿反复修改 publish.har.enable 的值")
            }
        }

        val name: HarNames = HarNames()

        fun name(nameOfHar: String) {
            require(enabled) { "har 未启用. 请先设置 publish.har { enabled = true }. " }
            name.nameOfHar = nameOfHar
            val baseSoName = nameOfHar.removePrefix("@").replace("/", "_")
            name.nameOfKmpSo = baseSoName
            name.nameOfBridgeSo = "${baseSoName}_bridge"
        }

        var version: String by xValue("") {
            require(enabled) { "har 未启用. 请先设置 publish.har { enabled = true }. " }
            require(it.isNotBlank()) { "har.version 不能为空。请参考右侧样例设置：publish.har { version = \"1.0.0\" }" }
            getHarExtension().harVersion = it
        }
        var harmonyAppModuleName: String by xValue("entry") {
            require(enabled) { "har 未启用. 请先设置 publish.har { enabled = true }. " }
            require(it.isNotBlank()) { "har.harmonyAppModuleName 不能为空，请参考右侧样例设置 publish.har { harmonyAppModuleName = \"entry\" }" }
            getHarExtension().rootModuleName = it
        }
        var includeSoList: List<String> by xValue(emptyList()) {
            require(enabled) { "har 未启用. 请先设置 publish.har { enabled = true }. " }
            getHarExtension().externalSoList = it
        }
        @Deprecated("即将下线")
        var linkedSoList: List<String> by xValue(emptyList()) {
            require(enabled) { "har 未启用. 请先设置 publish.har { enabled = true }. " }
            getHarExtension().linkSoList = it
        }
        var excludeSoList: List<String> by xValue(emptyList()) {
            require(enabled) { "har 未启用. 请先设置 publish.har { enabled = true }. " }
            getHarExtension().excludeSoName = it
        }

        var outputDir: File? by xValue {
            require(enabled) { "har 未启用. 请先设置 publish.har { enabled = true }. " }
            getHarExtension().localHarPath = it?.absolutePath
        }

        var enablePerformanceProbe: Boolean by xValue(false) {
            require(enabled) { "har 未启用. 请先设置 publish.har { enabled = true }. " }
            getHarExtension().enablePerformanceProbe = it
        }

        internal fun applyComposeEnabled(enabled: Boolean) {
            getHarExtension().enableCompose = enabled
        }

        internal fun applyComposeResourcesEnabled(enabled: Boolean) {
            getHarExtension().enableOhosResourceCompress = enabled
        }

        fun configureSharedLibs(configure: Action<SharedLibrary>) {
            getOrCreateSharedLibrary().forEach {
                configure.execute(it)
            }
        }

        fun configureSharedLibs(configure: Closure<*>) {
            getOrCreateSharedLibrary().forEach {
                project.configure(it, configure)
            }
        }

        internal fun checkValid() {
            if (!enabled) {
                return
            }
            require(name.nameOfHar.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! har.name.nameOfHar 不能为空，请参考右侧样例设置：publish.har { name.nameOfHar = \"@byte/kmp\" }" }
            require(name.nameOfKmpSo.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! har.name.nameOfKmpSo 不能为空，请参考右侧样例设置：publish.har { name.nameOfKmpSo = \"byte_kmp\" }" }
            require(name.nameOfBridgeSo.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! har.name.nameOfBridgeSo 不能为空，请参考右侧样例设置：publish.har { name.nameOfBridgeSo = \"bytekmp_kmp_bridge\" }" }
            require(version.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! har.version 不能为空。请参考右侧样例设置：publish.har { version = \"1.0.0\" }" }
            require(harmonyAppModuleName.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! har.harmonyAppModuleName 不能为空，请参考右侧样例设置 publish.har { harmonyAppModuleName = \"entry\" }" }
            // har 必须同时开启 ffi.arkTs
            requireNotNull(parentExtension.ffi._arkTs) {"[${project.path} <- 该项目配置异常]!! har 必须同时配置 ffi.arkTs 才可使用，请参考右侧样例设置 publish.arkTs { ... }" }
        }
    }

    inner class HarNames {
        var nameOfHar: String by xValue("") {
            require(it.isNotBlank()) { "har.name 不能为空，请参考右侧样例设置：publish.har { name = \"@byte/kmp\" }" }
            getHarExtension().harName = it
        }

        var nameOfKmpSo: String by xValue("") {
            require(it.isNotBlank()) { "har.nameOfKmpSo 不能为空，请参考右侧样例设置：publish.har { nameOfKmpSo = \"my_kmp\" }" }
            getOrCreateSharedLibrary().forEach { lib ->
                lib.baseName = it
            }
        }

        var nameOfBridgeSo: String by xValue("") {
            require(it.isNotBlank()) { "har.nameOfBridgeSo 不能为空，请参考右侧样例设置：publish.har { nameOfBridgeSo = \"my_kmp_bridge\" }" }
            getHarExtension().soName = it
        }
    }

    inner class XCFramework {
        private var _name: String? = null

        var _enabled = false
        var enabled : Boolean
            get() {
                return _enabled
            }
            set(value) {
                if(_enabled == value) return
                if (_enabled) { // true -> false
                    error("请勿反复修改 publish.xCframework.enable 的值")
                }
                if (this@PublishExtension._framework?._enabled == true) {
                    error("请勿同时设置 publish.xcframework{ enabled = true } 和 publish.framework { enabled = true }")
                }
//                if (this@PublishExtension._cocoaPods?._enabled == true) {
//                    error("请勿同时设置 publish.xcframework{ enabled = true } 和 publish.cocoaPods { enabled = true }")
//                }
                _enabled = true
            }

        var name: String
            get() {
                return _name
                    ?: error("XCFramework.name 不能为空。请参考右侧样例设置： xCFramework { name = \"xxxx\" } first.")
            }
            set(value) {
                require(enabled) { "xCframework 未启用. 请先设置 publish.xCFramework { enabled = true }. " }
                if (_name == null) {
                    _name = value
                    val xcConfig = project.XCFramework(value)
                    getOrCreateAllIosFramework().forEach {
                        xcConfig.add(it)
                    }
                } else if (_name != value) {
                    error("XCFramework.name 已设置，请勿重复设置. ")
                }
            }

        var static by xValue(false) {
            require(enabled) { "xCframework 未启用. 请先设置 publish.xCFramework { enabled = true }. " }
            require(_name != null) { "XCFramework.name 不能为空。请参考右侧样例设置： xCFramework { name = \"xxxx\" } first." }
            getOrCreateAllIosFramework().forEach {framework ->
                framework.isStatic = it
            }
        }

        fun configureFrameworks(configure: Action<org.jetbrains.kotlin.gradle.plugin.mpp.Framework>) {
            getOrCreateAllIosFramework().forEach {
                configure.execute(it)
            }
        }

        fun configureFrameworks(closure: Closure<*>) {
            getOrCreateAllIosFramework().forEach {
                project.configure(it, closure)
            }
        }

        internal fun checkValid() {
            if(!enabled) return
            require(_name != null) { "[${project.path} <- 该项目配置异常]!! XCFramework.name 不能为空。请参考右侧样例设置： xCFramework { name = \"xxxx\" } first." }
        }

    }

    inner class Framework {
        var _enabled: Boolean = false
        var enabled: Boolean
            get() {
                return _enabled
            }
            set(value) {
                if(_enabled == value) return
                if (_enabled) { // true -> false
                    error("请勿反复修改 publish.framework.enable 的值")
                }
                if (this@PublishExtension._xCFramework?._enabled == true) {
                    error("请勿同时设置 publish.xcframework{ enabled = true } 和 publish.framework { enabled = true }")
                }
//                if (this@PublishExtension._cocoaPods?._enabled == true) {
//                    error("请勿同时设置 publish.cocoaPods { enabled = true } 和 publish.framework { enabled = true }")
//                }
                _enabled = value
            }

        var name: String by xValue("") {
            require(enabled) { "framework 未启用. 请先设置 publish.framework { enabled = true }. " }
            getOrCreateAllIosFramework().forEach { framework -> framework.baseName = it}
        }

        var static: Boolean by xValue(false) {
            require(enabled) { "framework 未启用. 请先设置 publish.framework { enabled = true }. " }
            getOrCreateAllIosFramework().forEach { framework -> framework.isStatic = it }
        }

        fun configureFrameworks(configure: Action<org.jetbrains.kotlin.gradle.plugin.mpp.Framework>) {
            getOrCreateAllIosFramework().forEach {
                configure.execute(it)
            }
        }

        fun configureFrameworks(closure: Closure<*>) {
            getOrCreateAllIosFramework().forEach {
                project.configure(it, closure)
            }
        }

        internal fun checkValid() {
            if (!enabled) return
            require(name.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! framework.name 不能为空" }
        }
    }

//    @Incubating
//    inner class CocoaPods {
//
//        val iosArm64 = "arm64"
//        val iosX64 = "x64"
//        val iosSimulatorArm64 = "simulatorArm64"
//
//        val debug = "debug"
//        val release = "release"
//
//        internal var _enabled = false
//        var podSpec = Podspec()
//            get() {
//                require(enabled) { "CocoaPods 未启用. 请先设置 publish.cocoaPods { enabled = true }. " }
//                return field
//            }
//
//        fun podSpec(configure: Action<Podspec>) {
//            configure.execute(podSpec)
//        }
//
//        fun podSpec(closure: Closure<*>) {
//            project.configure(podSpec, closure)
//        }
//
//        var enabled : Boolean
//            get() {
//                return _enabled
//            }
//            set(value) {
//                if(_enabled == value) return
//                if (_enabled) { // true -> false
//                    error("请勿反复修改 publish.xCframework.enable 的值")
//                }
//                if (this@PublishExtension._framework?._enabled == true) {
//                    error("请勿同时设置 publish.cocoaPods { enabled = true } 和 publish.framework { enabled = true }")
//                }
//                if (this@PublishExtension._xCFramework?.enabled == true) {
//                    error("请勿同时设置 publish.cocoaPods { enabled = true } 和 publish.xcFramework { enabled = true }")
//                }
//                _enabled = true
//                project.applyIfAbsent("com.bytedance.kmp.ios.framework_generator")
//            }
//
//        var name by xValue("") {
//            require(enabled) { "CocoaPods 未启用. 请先设置 publish.cocoaPods { enabled = true }. " }
//            getOrCreateAllIosFramework().forEach { framework ->
//                framework.baseName = it
//            }
//        }
//        @set:JvmName("setIsStatic")
//        var isStatic by xValue(false) {
//            require(enabled) { "CocoaPods 未启用. 请先设置 publish.cocoaPods { enabled = true }. " }
//            getOrCreateAllIosFramework().forEach { framework ->
//                framework.isStatic = it
//            }
//        }
//        var buildMode by xValue("release") {
//            require(enabled) { "CocoaPods 未启用. 请先设置 publish.cocoaPods { enabled = true }. " }
//            getIOSProjectConfig().buildMode = it
//        }
//        var buildTargets : List<String> by xValue(emptyList()) {
//            require(enabled) { "CocoaPods 未启用. 请先设置 publish.cocoaPods { enabled = true }. " }
//            if (this.toTypedArray().contentEquals(it.toTypedArray())) return@xValue
//            if (this.isEmpty()) {
//                getIOSProjectConfig().buildTargets = it.joinToString("|")
//            } else {
//                error("请勿重复设置 ffi.cocaPods.buildTargets 的值")
//            }
//        }
//
//        fun buildTargets(vararg target: String) {
//            require(enabled) { "CocoaPods 未启用. 请先设置 publish.cocoaPods { enabled = true }. " }
//            buildTargets = target.toList()
//        }
//
//        var outputDir : File? by xValue {
//            require(enabled) { "CocoaPods 未启用. 请先设置 publish.cocoaPods { enabled = true }. " }
//            getIOSProjectConfig().iOSBuildProductDirectory = it?.absolutePath ?: ""
//        }
//
//        var enableBitsPackaging by xValue(false) {
//            require(enabled) { "CocoaPods 未启用. 请先设置 publish.cocoaPods { enabled = true }. " }
//            getIOSProjectConfig().enableBitsPackaging = it
//        }
//
//        fun configureFrameworks(configure: org.jetbrains.kotlin.gradle.plugin.mpp.Framework.() -> Unit) {
//            getOrCreateAllIosFramework().forEach {
//                configure(it)
//            }
//        }
//
//        internal fun checkValid() {
//            if(!enabled) return
//            require(name.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! cocoaPods.name 不能为空，请参考右侧样例设置：publish.cocoaPods { name = \"launcher\" }" }
//            require(buildTargets.isNotEmpty()) { "[${project.path} <- 该项目配置异常]!! cocoaPods.buildTargets 不能为空，请参考右侧样例设置：publish.cocoaPods { buildTargets = iosArm64 }" }
//            require(podSpec.fileName.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! cocoaPods.podSpec.fileName 不能为空，请参考右侧样例设置：publish.cocoaPods { podSpec.fileName = \"xxxxxx\" }" }
//            require(podSpec.version.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! cocoaPods.podSpec.version 不能为空，请参考右侧样例设置：publish.cocoaPods { podSpec.version = \"xxxxxx\" }" }
//            require(podSpec.summary.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! cocoaPods.podSpec.summary 不能为空，请参考右侧样例设置：publish.cocoaPods { podSpec.summary = \"xxxxxx\" }" }
//            require(podSpec.description.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! cocoaPods.podSpec.description 不能为空，请参考右侧样例设置：publish.cocoaPods { podSpec.description = \"xxxxxx\" }" }
//            require(podSpec.source.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! cocoaPods.podSpec.source 不能为空，请参考右侧样例设置：publish.cocoaPods { podSpec.source = \"xxxxxx\" }" }
//            require(podSpec.homepage.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! cocoaPods.podSpec.homepage 不能为空，请参考右侧样例设置：publish.cocoaPods { podSpec.homepage = \"xxxxxx\" }" }
//            require(podSpec.iOSDevelopmentTarget.isNotBlank()) { "[${project.path} <- 该项目配置异常]!! cocoaPods.podSpec.iOSDevelopmentTarget 不能为空，请参考右侧样例设置：publish.cocoaPods { podSpec.iOSDevelopmentTarget = \"xxxxxx\" }" }
//        }
//    }

//    inner class Podspec {
//        var fileName: String by xValue("") {
//            getIOSProjectConfig().podspecFileName = it
//        }
//        var version: String by xValue("") {
//            getIOSProjectConfig().podspecVersion = it
//        }
//        var summary: String by xValue("") {
//            getIOSProjectConfig().podspecSummary = it
//        }
//        var description: String by xValue("") {
//            getIOSProjectConfig().podspecDescription = it
//        }
//        var source: String by xValue("") {
//            getIOSProjectConfig().podspecSource = it
//        }
//        var homepage: String by xValue("") {
//            getIOSProjectConfig().podspecHomePage = it
//        }
//        @set:JvmName("setiOSDevelopmentTarget")
//        @get:JvmName("getiOSDevelopmentTarget")
//        var iOSDevelopmentTarget: String by xValue("") {
//            getIOSProjectConfig().podspecIOSDevelopmentTarget = it
//        }
//    }

    @Suppress("DEPRECATION")
    private fun getHarExtension(): HarExtension {
        return project.extensions.findByName(SoHarGeneratorPlugin.EXTENSION_NAME) as HarExtension
    }

//    private fun getIOSProjectConfig(): IOSProjectConfig {
//        return project.extensions.findByName("iOSProjectConfig") as IOSProjectConfig
//    }

    private fun getOrCreateSharedLibrary(): List<SharedLibrary> {
        val ohosTargets = if (parentExtension.isIOS) {
            emptyList()
        } else {
            listOf(kmpExtension.ohosArm64())
        }
        val allSharedLibrary = ohosTargets.getAllSharedLibrary()
        if (allSharedLibrary.isEmpty()) {
            ohosTargets.forEach {
                it.binaries.sharedLib { }
            }
        }

        return ohosTargets.getAllSharedLibrary()
    }

    private fun List<KotlinNativeTarget>.getAllSharedLibrary(): List<SharedLibrary> {
        return flatMap {
            it.binaries
                .filterIsInstance<SharedLibrary>()
                .filter { it.outputKind == NativeOutputKind.DYNAMIC }
        }
    }

    internal fun getOrCreateAllIosFramework(): List<org.jetbrains.kotlin.gradle.plugin.mpp.Framework> {
        return listOf(kmpExtension.iosArm64(), kmpExtension.iosX64(), kmpExtension.iosSimulatorArm64())
            .flatMap { getOrCreateFramework(it) }
    }

    private fun getOrCreateFramework(taget: KotlinNativeTarget): List<org.jetbrains.kotlin.gradle.plugin.mpp.Framework> {
        return when {
            (taget.konanTarget == KonanTarget.IOS_X64) || (taget.konanTarget == KonanTarget.IOS_ARM64) || (taget.konanTarget == KonanTarget.IOS_SIMULATOR_ARM64) -> {
                val frameworks = taget.binaries.filterIsInstance<org.jetbrains.kotlin.gradle.plugin.mpp.Framework>()
                if(frameworks.isNotEmpty()) {
                    frameworks
                } else {
                    taget.binaries.framework()
                    taget.binaries.filterIsInstance<org.jetbrains.kotlin.gradle.plugin.mpp.Framework>()
                }

            }
            else -> emptyList()
        }
    }

    internal fun checkValid() {
        _klib?.takeIf { it.enabled }?.checkValid()
        _har?.takeIf { it.enabled }?.checkValid()
        _framework?.takeIf { it.enabled }?.checkValid()
        _xCFramework?.takeIf { it.enabled }?.checkValid()
//        _cocoaPods?.takeIf { it.enabled }?.checkValid()
    }
}
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

package com.bytedance.kotlin.multiplatform

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.kotlinToolingVersion
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

class Versions {
    companion object {
        const val CURRENT_VERSION = "1.0.0-bytekmp.3"
        const val COMPOSE_VERSION = "1.6.10-bytekmp.10"
        const val COMPOSE_RESOURCES_VERSION = "1.6.10-bytekmp.10"
        const val LIFECYCLE_VERSION = "2.8.0-bytekmp.10"
        const val NAVIGATION_VERSION = "2.7.7-bytekmp.10"
        const val SAVESTATE_VERSION = "1.2.1-bytekmp.10"
        const val IOS_PLUGIN_VERSION = "1.2.1"

        internal fun checkKotlinVersion(project: Project) {
            if(project.findProperty("bytekmp.disable.kotlin.version.check")?.toString()?.toBoolean() != true) {
                val version = project.kotlinToolingVersion
                require(version.major == 2 && (version.minor == 0 || version.minor == 1)) {
                    error("不支持的 kotlin-gradle-plugin 版本($version)，请使用 2.0.20-eap-12034（for AGP 410）或 2.0.255-beta-10048 (for AGP7) 以上版本")
                }

                when {
                    version.classifier?.startsWith("bytekmp.beta.") == true -> {
                        // >= 2.0.255-bytekmp.beta.48
                        val subVersion = version.classifier?.substringAfter("bytekmp.beta.")?.toIntOrNull() ?: -1
                        if(subVersion < 48) {
                            error("kotlin-gradle-plugin 版本号过低($version)，请使用 2.0.255-bytekmp.beta.48 以上版本")
                        }
                    }
                    version.classifier?.startsWith("beta-") == true -> {
                        // >= 2.0.255-10048
                        if(version.compareTo(KotlinToolingVersion("2.0.255-beta-10048")) < 0) {
                            error("kotlin-gradle-plugin 版本号过低($version)，请使用 2.0.255-beta-10048 以上版本")
                        }
                    }
                    version.classifier?.startsWith("ea-") == true -> {
                        // >= 2.0.20-ea-2032
                        if(version.compareTo(KotlinToolingVersion("2.0.20-ea-2032")) < 0) {
                            error("kotlin-gradle-plugin 版本号过低($version)，请使用 2.0.20-ea-2032 以上版本")
                        }
                    }
                    version.classifier?.startsWith("eap-") == true -> {
                        // >= 2.0.20-ea-2032
                        if(version.compareTo(KotlinToolingVersion("2.0.20-eap-12034")) < 0) {
                            error("kotlin-gradle-plugin 版本号过低($version)，请使用 2.0.20-eap-12034 以上版本")
                        }
                    }
                    else -> {
                        error("不支持的 kotlin-gradle-plugin 版本($version)，请使用 2.0.20-eap-12034（for AGP 410）或 2.0.255-beta-10048 (for AGP7) 以上版本")
                    }
                }
            }
        }
    }


}
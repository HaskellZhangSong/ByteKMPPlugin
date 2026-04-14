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

package com.bytedance.kotlin.multiplatform.gradle.publish

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.PublishingExtension
import java.net.URI
import java.util.*

open class KmpPublishingExtension(
    private val project: Project,
    publishingExtension: PublishingExtension
) {

    private val localProperties = project.rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.let { Properties().apply { load(it.inputStream().buffered()) } }
        ?: Properties()

    init {
        val url = tryGetProperty("custom_maven_publish_url")
        if (url != null) {
            publishingExtension.repositories.maven {
                it.url = URI.create(url)
                it.name = "Bytedance"
                it.credentials {
                    it.username = tryGetProperty("custom_maven_publish_username")
                    it.password = tryGetProperty("custom_maven_publish_password")
                }
            }
        }
    }

    init {
        publishingExtension.publications
    }

    var group: String
        set(value) {
            project.group = value.trim()
        }
        get() {
            return project.group.toString()
        }

    val artifact: String
        get() {
            return project.name.toString()
        }

    var version: String
        set(value) {
            project.version = value.trim()
        }
        get() {
            return project.version.toString()
        }

    private fun tryGetProperty(propertyName: String): String? {
        return localProperties.getProperty(propertyName)
            ?: project.findProperty(propertyName)?.toString()
            ?: System.getenv(propertyName)
    }

    companion object {
        const val KMP_EXTENSION_NAME = "bytekmp_klib_publish"
    }

}
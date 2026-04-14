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

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import java.util.Properties

open class BuildProperties(private val project: Project, private val rootLocalProperties: LocalProperties) {
    fun get(key: String): Any? {
        return rootLocalProperties.get(key) ?: project.findProperty(key)
    }
}

open class LocalProperties(project: Project) {
    private val localProperties = Properties().apply {
        project.rootProject.file("local.properties").takeIf { it.isFile }
            ?.let { load(it.inputStream().buffered()) }
    }

    fun get(key: String): String? {
        return localProperties.getProperty(key)
    }
}

val Project.localProperties: LocalProperties
    get() {
        val rootProject = project.rootProject
        return rootProject.extensions.getOrCreate("bytekmp.localProperties", LocalProperties::class.java, rootProject)
    }

val Project.buildProperties: BuildProperties
    get() {
        return project.extensions.getOrCreate(
            "bytekmp.buildProperties",
            BuildProperties::class.java,
            this,
            this.localProperties
        )

    }

@Suppress("UNCHECKED_CAST")
internal fun <T> ExtensionContainer.getOrCreate(name: String, clazz: Class<T>, vararg varargs: Any?): T {
    val value = this.findByName(name) as? T
    if (value != null) {
        return value
    }
    val newResult = create(name, clazz, *varargs)
    return newResult
}
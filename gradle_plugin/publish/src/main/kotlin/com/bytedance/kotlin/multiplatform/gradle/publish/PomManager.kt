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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import java.io.*

class PomManager(val project: Project) {

    class GitHelper(private val project: Project) {

        fun getGitUrl(): String {
            val stdout = ByteArrayOutputStream()
            project.exec {
                it.commandLine = listOf("sh", "-c", "git config --get remote.origin.url")
                it.standardOutput = stdout
            }
            var gitUrl = stdout.toString().trim()
            stdout.close()

            // deal with bytebus clone code with ci_testing
            if (gitUrl.startsWith("https://ci_testing:")) {
                val index = gitUrl.indexOf("@")
                gitUrl = "https://" + gitUrl.substring(index + 1)
            }
            return gitUrl
        }

        fun getGitSha(): String {
            val stdout = ByteArrayOutputStream()
            project.exec {
                it.commandLine = listOf("sh", "-c", "git rev-parse HEAD")
                it.standardOutput = stdout
            }
            return stdout.toString().trim()
        }

        fun getModuleGitSha(project: Project): String {
            val stdout = ByteArrayOutputStream()
            project.exec {
                it.commandLine = listOf("sh", "-c", "git log --pretty=format:\"%H\" -n 1 " + project.projectDir.path)
                it.standardOutput = stdout
            }
            return stdout.toString().trim()
        }

        fun getProject(): String {
            return project.name
        }
    }

    /**
     * 支持自定义参数写入pom, 位于project目录gradle.properties
     * @sample POM_PARAMS={"key1":"value","key2":1}
     */
    class ParamsHelper(val project: Project) {

        fun parse(): Map<*, *>? {
            try {
                if (!project.hasProperty(POM_PARAMS)) {
                    return null
                }
            } catch (ignore: Exception) {
                return null
            }
            val paramsString = project.property(POM_PARAMS) as String
            val jsonSlurper = JsonSlurper()
            val result = jsonSlurper.parseText(paramsString)
            if (result != null && result is Map<*, *>) {
                return result
            }
            throw RuntimeException("`" + POM_PARAMS + "`" + " parse error! `${paramsString}` is not json string!")
        }
    }

//    static void inject(project)
//    {
//        new PomManager (project).registerTask()
//    }

    private val gitHelper = GitHelper(project)
    private val paramsHelper = ParamsHelper(project)

    private fun registerTask() {
        project.tasks.withType(GenerateMavenPom::class.java) { task ->
            task.doLast {
                modify(task.destination)
            }
        }
    }

    fun modify(file: File) {
        if (!file.exists()) {
            throw FileNotFoundException("file is not exists `" + file.path + "`")
        }
        val document = SAXBuilder().build(FileInputStream(file))
        val rootElement = document.rootElement
        // 查找version元素
        val versionElement = rootElement.getChild("version", rootElement.namespace);
        val appendData = mutableMapOf<Any, Any>()
        appendRepoInfo(appendData)
        if (versionElement != null) {
            // 创建一个新元素 key
            val keyElement = Element(POM_KEY, rootElement.namespace);
            keyElement.setText(JsonBuilder(appendData).toString());
            // 将key元素添加到version元素的同级
            val versionIndex = rootElement.indexOf(versionElement);
            rootElement.addContent(versionIndex + 1, keyElement);
        } else {
            println("version 元素未找到。");
        }

        // 保存修改后的POM文件
        val xmlOutputter = XMLOutputter()
        xmlOutputter.format = Format.getPrettyFormat();
        xmlOutputter.output(document, FileOutputStream(file));
    }

    private fun appendRepoInfo(map: MutableMap<Any, Any>) {
        map["git"] = gitHelper.getGitUrl()
        map["project"] = gitHelper.getProject()
        map["sha1"] = gitHelper.getGitSha()
        if (project.hasProperty("ADD_MODULE_SHA1") && project.property("ADD_MODULE_SHA1") != null) {
            map["module_sha1"] = gitHelper.getModuleGitSha(project)
        }
    }

    companion object {
        private const val POM_PARAMS = "POM_PARAMS"
        private const val POM_KEY = "description"

        fun registerTask(project: Project) {
            PomManager(project).registerTask()
        }
    }
}

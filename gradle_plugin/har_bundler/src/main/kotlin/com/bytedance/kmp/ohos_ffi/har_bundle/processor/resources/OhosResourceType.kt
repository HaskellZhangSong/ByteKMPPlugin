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

package com.bytedance.kmp.ohos_ffi.har_bundle.processor.resources

import org.gradle.api.logging.Logger
import org.w3c.dom.Node
import java.io.File
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.relativeTo

internal data class OhosResourceItem(
    val type: ResourceType,
    val name: String,
    val file: File,
    val path: Path,
    val qualifiers: List<String> = emptyList(),
)

private val validResDirs = setOf(
    "drawable",
    "font",
    "values",
    "files"
)

internal fun File.toOhosResourceItem(relativePath: Path): List<OhosResourceItem>? {
    val file = this
    val dirName = file.parentFile.name ?: return null
    val typeAndQualifiers = dirName.split("-")
    if (typeAndQualifiers.isEmpty()) return null

    val typeString = typeAndQualifiers.first().lowercase()
    val qualifiers = typeAndQualifiers.takeLast(typeAndQualifiers.size - 1)
    val path = file.toPath().relativeTo(relativePath)

    if (typeString == "files" || typeString == "font") {
        return null
    }

    if (typeString == "values") {
        return getValueResourceItems(qualifiers, path)
    }

    val type = ResourceType.Companion.fromString(typeString) ?: error("Unknown resource type: '$typeString'.")
    return listOf(OhosResourceItem(type, file.nameWithoutExtension.asUnderscoredIdentifier(), file, path, qualifiers))
}

/**
 * 当前 file 的文件类型为 ohos string.xml 资源文件
 * 将 file 中的 key 统一加上 resourcePrefix
 */
internal fun File.parseStringXmlItemKeyByResourcePrefix(resourcePrefix: String, targetFile: File) {
    // 创建 DocumentBuilder 实例用于解析 XML 文件
    val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val doc = docBuilder.parse(this)

    // 获取所有 resources 元素
    val stringNodes = doc.getElementsByTagName("resources").item(0).childNodes
    for (i in 0 until stringNodes.length) {
        val stringNode = stringNodes.item(i) as Node
        if (stringNode.hasAttributes()) {
            val nameAttr = stringNode.attributes.getNamedItem("name")
            if (nameAttr != null) {
                nameAttr.nodeValue = resourcePrefix + nameAttr.nodeValue
            }
        }
    }

    // 将修改后的 Document 写回文件
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.transform(DOMSource(doc), StreamResult(targetFile))
}

private fun File.getValueResourceItems(
    qualifiers: List<String>,
    path: Path,
): List<OhosResourceItem> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(this)
    val items = doc.getElementsByTagName("resources").item(0).childNodes
    val result = List(items.length) { items.item(it) }
        .filter { it.hasAttributes() }
        .map { getValueItem(it, this, path, qualifiers) }
    return result
}

private fun getValueItem(node: Node, file: File, path: Path, qualifiers: List<String>): OhosResourceItem {
    val type = ResourceType.Companion.fromString(node.nodeName)
        ?: error("Unknown resource type: '${node.nodeName}'.")
    val key = node.attributes.getNamedItem("name")?.nodeValue ?: error("Attribute 'name' not found.")
    return OhosResourceItem(type, key, file, path, qualifiers)
}

internal fun File.checkResourceDirValid() {
    listNotHiddenFiles().forEach {
        if (!it.isDirectory) {
            error("${it.name} is not directory! Raw files should be placed in '${name}/files' directory.")
        }
    }
}

internal fun File.checkResourceValid(logger: Logger): Boolean {
    if (isFile) {
        val dirName = parentFile.nameWithoutExtension
        val typeAndQualifiers = dirName.split("-")
        if (typeAndQualifiers.isEmpty()) return false

        val typeString = typeAndQualifiers.first().lowercase()

        if (!validResDirs.contains(typeString)) {
            logger.warn("handle resource '${path}' skip,  because it is not in valid dirs")
            return false
        }

        val extension = extension
        return when (typeString) {
            "drawable" -> {
                extension in setOf("png", "jpg", "jpeg", "webp", "gif", "svg", "xml")
            }
            "font" -> {
                extension in setOf("ttf", "otf", "ttc")
            }
            "files" -> {
                true
            }
            else -> {
                // values:
                extension in setOf("xml")
            }
        }
    }
    return false
}

internal fun File.listNotHiddenFiles(): List<File> =
    listFiles()?.filter { !it.isHidden }.orEmpty()

internal fun String.asUnderscoredIdentifier(): String =
    replace('-', '_')
        .let { if (it.isNotEmpty() && it.first().isDigit()) "_$it" else it }
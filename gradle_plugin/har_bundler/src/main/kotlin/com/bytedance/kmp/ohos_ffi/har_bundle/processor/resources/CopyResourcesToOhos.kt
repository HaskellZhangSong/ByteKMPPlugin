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

import org.gradle.api.Project
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 将 font 目录与 files 目录下的文件拷贝到 rawFileDir 目录下
 */
internal fun Project.copyFilesResourceToRawFile(
    topLevelResourceDir: List<File>,
    rawFileDir: String,
) {
    topLevelResourceDir.forEach { topLevelDir ->
        // 处理 font 目录
        val fontDir = File(topLevelDir, "font")
        if (fontDir.exists() && fontDir.isDirectory) {
            fontDir.listNotHiddenFiles().forEach { fontFile ->
                val desFontFile = File(rawFileDir, "font/${fontFile.name}")
                if (desFontFile.exists()) {
                    project.logger.warn("Duplicate kmp font resource overwrites ${desFontFile.name}")
                }
                fontFile.copyTo(desFontFile, true)
            }
        }
        // 处理 files 目录
        // files 内会存在多级目录结构，需要先计算所以叶子节点的文件相对于 files 的相对路径，然后再拷贝到 rawFileDir 目录下
        val filesDir = File(topLevelDir, "files")
        if (filesDir.exists() && filesDir.isDirectory) {
            filesDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = file.relativeTo(filesDir).path
                val desFile = File(rawFileDir, "files/$relativePath")
                if (desFile.exists()) {
                    project.logger.warn("Duplicate kmp file resource overwrites ${desFile.name}")
                }
                file.copyTo(desFile, true)
            }
        }
    }
}

internal fun Project.handleAndCopyDrawableAndValuesResource(
    topLevelResourceDir: List<File>,
    desResourceDir: String
): List<ResourceRefItem> {
    val valueResourceFiles = mutableMapOf<String, List<File>>()
    val result = mutableListOf<ResourceRefItem>()
    topLevelResourceDir.forEach { topLevelDir ->
        topLevelDir.listNotHiddenFiles().forEach { resourceDir ->
            // drawable/values
            val dirName = resourceDir.nameWithoutExtension
            val typeAndQualifiers = dirName.split("-")
            val type = typeAndQualifiers.first()
            if (type == "drawable") {
                resourceDir.listNotHiddenFiles().forEach { drawableFile ->
                    val desDrawableFile = File(
                        getParentDirByQualifier(drawableFile, desResourceDir),
                        drawableFile.name
                    )
                    if (desDrawableFile.exists()) {
                        project.logger.warn("Duplicate kmp drawable resource overwrites ${desDrawableFile.name}")
                    } else {
                        result.add(ResourceRefItem("media", drawableFile.nameWithoutExtension))
                    }
                    desDrawableFile.mkdirs()
                    drawableFile.copyTo(desDrawableFile, true)
                }
            }

            if (type == "values") {
                resourceDir.listNotHiddenFiles().filter { it.name.endsWith(".xml") }.forEach { valuesFile ->

                    val key = getParentDirByQualifier(valuesFile, desResourceDir)
                    valueResourceFiles[key] = valueResourceFiles[key]?.plus(valuesFile) ?: listOf(valuesFile)
                }
            }
        }
    }

    convertValuesXmlToJson(valueResourceFiles, result)
    return result
}

private fun Project.convertValuesXmlToJson(sourceFiles: Map<String, List<File>>, result: MutableList<ResourceRefItem>) {

    sourceFiles.entries.forEach { entry ->
        val destDir = File(entry.key)
        val sourceFiles = entry.value

        var stringItems = mutableListOf<OhosString>()
        val pluralItems = mutableListOf<OhosPlural>()
        val stringArrayItems = mutableListOf<OhosStringArray>()

        sourceFiles.forEach { file ->

            val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = docBuilder.parse(file)
            val root = doc.documentElement // <resources>

            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node.nodeType != Node.ELEMENT_NODE) continue

                val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
                when (node.nodeName) {
                    "string" -> {
                        stringItems.add(OhosString(name, node.textContent))
                    }

                    "string-array" -> {
                        val items = mutableListOf<OhosStringArrayItem>()
                        val itemNodes = node.childNodes
                        for (j in 0 until itemNodes.length) {
                            val itemNode = itemNodes.item(j)
                            if (itemNode.nodeType == Node.ELEMENT_NODE && itemNode.nodeName == "item") {
                                items.add(OhosStringArrayItem(itemNode.textContent))
                            }
                        }
                        if (items.isNotEmpty()) {
                            stringArrayItems.add(OhosStringArray(name, items))
                        }
                    }

                    "plurals" -> {
                        val items = mutableListOf<OhosPluralItem>()
                        val itemNodes = node.childNodes
                        for (j in 0 until itemNodes.length) {
                            val itemNode = itemNodes.item(j)
                            if (itemNode.nodeType == Node.ELEMENT_NODE && itemNode.nodeName == "item") {
                                val quantity = itemNode.attributes.getNamedItem("quantity")?.nodeValue ?: continue
                                items.add(OhosPluralItem(quantity, itemNode.textContent))
                            }
                        }
                        if (items.isNotEmpty()) {
                            pluralItems.add(OhosPlural(name, items))
                        }
                    }
                }
            }
        }
        // 确保目标目录存在
        destDir.mkdirs()

        // string 去重
        stringItems = stringItems.distinctBy {
            it.name + "" + it.value
        }.toMutableList()

        stringItems.forEach {
            result.add(ResourceRefItem("string", it.name))
        }
        pluralItems.forEach {
            result.add(ResourceRefItem("plural", it.name))
        }
        pluralItems.forEach {
            result.add(ResourceRefItem("strarray", it.name))
        }

        // 辅助函数：转义JSON字符串中的特殊字符
        fun String.escapeJson(): String = this.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        // 生成 string.json
        if (stringItems.isNotEmpty()) {
            val jsonContent =
                stringItems.joinToString(separator = ",\n", prefix = "{\n    \"string\": [\n", postfix = "\n    ]\n}") {
                    "        {\n            \"name\": \"${it.name.escapeJson()}\",\n            \"value\": \"${it.value.escapeJson()}\"\n        }"
                }
            File(destDir, "string.json").writeText(jsonContent)
        }

        // 生成 plural.json
        if (pluralItems.isNotEmpty()) {
            val jsonContent = pluralItems.joinToString(
                separator = ",\n",
                prefix = "{\n    \"plural\": [\n",
                postfix = "\n    ]\n}"
            ) { plural ->
                val values = plural.value.joinToString(separator = ",\n") { item ->
                    "                {\n                    \"quantity\": \"${item.quantity.escapeJson()}\",\n                    \"value\": \"${item.value.escapeJson()}\"\n                }"
                }
                "        {\n            \"name\": \"${plural.name.escapeJson()}\",\n            \"value\": [\n$values\n            ]\n        }"
            }
            File(destDir, "plural.json").writeText(jsonContent)
        }

        // 生成 strarray.json
        if (stringArrayItems.isNotEmpty()) {
            val jsonContent = stringArrayItems.joinToString(
                separator = ",\n",
                prefix = "{\n    \"strarray\": [\n",
                postfix = "\n    ]\n}"
            ) { strArray ->
                val values = strArray.value.joinToString(separator = ",\n") { item ->
                    "                {\n                    \"value\": \"${item.value.escapeJson()}\"\n                }"
                }
                "        {\n            \"name\": \"${strArray.name.escapeJson()}\",\n            \"value\": [\n$values\n            ]\n        }"
            }
            File(destDir, "strarray.json").writeText(jsonContent)
        }
    }
}

private data class OhosString(val name: String, val value: String)
private data class OhosPluralItem(val quantity: String, val value: String)
private data class OhosPlural(val name: String, val value: List<OhosPluralItem>)
private data class OhosStringArrayItem(val value: String)
private data class OhosStringArray(val name: String, val value: List<OhosStringArrayItem>)


/**
 * @param targetFile 目标文件
 * @return 限定符表述
 */
private fun Project.getParentDirByQualifier(targetFile: File, desResourceDir: String): String {
    // 所有支持的语言代码，符合 ISO 639-1 标准
    val languageQualifiers = listOf(
        "en",
        "es",
        "fr",
        "de",
        "it",
        "ja",
        "ko",
        "ru",
        "zh",
        "ar",
        "he",
        "fa",
        "tr",
        "tr",
        "pt",
        "pl",
        "id",
        "vi",
        "th",
        "hi",
        "bn",
        "ms",
        "tl",
        "sw",
        "ug",
        "ur",
        "pa",
        "gu",
        "te",
        "kn",
        "ml",
        "mr",
        "as",
        "cs",
        "da",
        "nl",
        "fi",
        "sv",
        "no"
    )
    // 所有支持的地区代码，符合 ISO 3166-1 标准
    val regionQualifiers = listOf(
        "AD", "AE", "AF", "AG", "AI", "AL", "AM", "AO", "AQ", "AR", "AS", "AT", "AU", "AW", "AX", "AZ",
        "BA", "BB", "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BL", "BM", "BN", "BO", "BQ", "BR", "BS",
        "BT", "BV", "BW", "BY", "BZ", "CA", "CC", "CD", "CF", "CG", "CH", "CI", "CK", "CL", "CM", "CN",
        "CO", "CR", "CU", "CV", "CW", "CX", "CY", "CZ", "DE", "DJ", "DK", "DM", "DO", "DZ", "EC", "EE",
        "EG", "EH", "ER", "ES", "ET", "FI", "FJ", "FK", "FM", "FO", "FR", "GA", "GB", "GD", "GE", "GF",
        "GG", "GH", "GI", "GL", "GM", "GN", "GP", "GQ", "GR", "GS", "GT", "GU", "GW", "GY", "HK", "HM",
        "HN", "HR", "HT", "HU", "ID", "IE", "IL", "IM", "IN", "IO", "IQ", "IR", "IS", "IT", "JE", "JM",
        "JO", "JP", "KE", "KG", "KH", "KI", "KM", "KN", "KP", "KR", "KW", "KY", "KZ", "LA", "LB", "LC",
        "LI", "LK", "LR", "LS", "LT", "LU", "LV", "LY", "MA", "MC", "MD", "ME", "MF", "MG", "MH", "MK",
        "ML", "MM", "MN", "MO", "MP", "MQ", "MR", "MS", "MT", "MU", "MV", "MW", "MX", "MY", "MZ", "NA",
        "NC", "NE", "NF", "NG", "NI", "NL", "NO", "NP", "NR", "NU", "NZ", "OM", "PA", "PE", "PF", "PG",
        "PH", "PK", "PL", "PM", "PN", "PR", "PS", "PT", "PW", "PY", "QA", "RE", "RO", "RS", "RU", "RW",
        "SA", "SB", "SC", "SD", "SE", "SG", "SH", "SI", "SJ", "SK", "SL", "SM", "SN", "SO", "SR", "SS",
        "ST", "SV", "SX", "SY", "SZ", "TC", "TD", "TF", "TG", "TH", "TJ", "TK", "TL", "TM", "TN", "TO",
        "TR", "TT", "TV", "TW", "TZ", "UA", "UG", "UM", "US", "UY", "UZ", "VA", "VC", "VE", "VG", "VI",
        "VN", "VU", "WF", "WS", "YE", "YT", "ZA", "ZM", "ZW"
    ).map { "r$it" }

    val kmpComposeDpi = listOf("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

    val dirName = targetFile.parentFile.nameWithoutExtension
    val typeAndQualifiers = dirName.split("-")
    val type = typeAndQualifiers.first()
    val qualifiers = typeAndQualifiers.takeLast(typeAndQualifiers.size - 1)

    // 1. 如果存在 light, 将 light 去掉， 如果存在 dark, 替换为 night
    // 2. 如果 language 限定符存在，调整到第一位
    // 3. 如果 region 限定符存在，调整到第二位
    // 2. 如果 dpi 限定符存在，调整到第三位
    // 5. 如果 theme 限定符存在，调整到最后一位
    val newQualifiers = Array(4) { "" }
    qualifiers.forEach { qualifier ->
        when (qualifier) {
            in languageQualifiers -> newQualifiers[0] = qualifier
            in regionQualifiers -> newQualifiers[1] = qualifier.removePrefix("r")
            // 修正点：根据注释正确处理主题
            "dark" -> newQualifiers[2] = "dark"
            "light" -> newQualifiers[2] = "" // light 是默认主题，对应空字符串
            in kmpComposeDpi -> newQualifiers[3] = qualifier.dpiMap()
            else -> {
                project.logger.warn("Illegal resource qualifier: $qualifier, resource name :${targetFile.name}")
            }
        }
    }

    val (language, region, theme, dpi) = newQualifiers

    val langAndRegionPart = listOf(language, region).filter { it.isNotBlank() }.joinToString("_")

    val qualifierString = listOf(langAndRegionPart, theme, dpi)
        .filter { it.isNotBlank() }
        .joinToString("-")

    if (qualifierString.isNotBlank()) {
        if (type == "drawable") {
            return "$desResourceDir/$qualifierString/media"
        }
        if (type == "values") {
            return "$desResourceDir/$qualifierString/element"
        }
    }

    // 默认情况
    return when (type) {
        "drawable" -> "$desResourceDir/base/media"
        "values" -> {
            "$desResourceDir/base/element"
        }

        else -> {
            ""
        }
    }
}

private fun String.dpiMap(): String {
    return when (this) {
        "ldpi" -> "sdpi"
        "mdpi" -> "mdpi"
        "hdpi" -> "ldpi"
        "xhdpi" -> "xldpi"
        "xxhdpi" -> "xxldpi"
        "xxxhdpi" -> "xxxldpi"
        else -> this
    }
}
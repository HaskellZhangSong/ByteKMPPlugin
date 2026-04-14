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

package com.bytedance.kmp.spi.common

import com.bytedance.kmp.spi.ohos.kotlin.SpiImplMetaInfo
import com.bytedance.kmp.spi.ohos.kotlin.SpiInitCodeGenerator
import com.bytedance.ksp.metainfo.KspMetaInfoManager
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Visibility

class SPIMetaInfoGenerator(val clazz: KSClassDeclaration, val environment: SymbolProcessorEnvironment, val resolver: Resolver) {

    companion object {
        const val ANNOTATION_SPI_IMPL = "com.bytedance.kmp.spi.annotation.KmpSpiImpl"
        const val SPI_IMPL_META_INFO_GENERATE_PACKAGE = "kmp_spi_impl_meta_info"
        const val SPI_IMPL_META_INFO_KEY = "spi_impl_meta_info_key"
        const val KMP_SPI_INTERFACE = "com.bytedance.kmp.spi.IKmpService"

        // 为模块生成 metainfo
        fun generateMetaInfo(environment: SymbolProcessorEnvironment, resolver: Resolver, skipWrite: Boolean = false): List<SpiImplMetaInfo> {
            return resolver.getSymbolsWithAnnotation(ANNOTATION_SPI_IMPL)
                .filterIsInstance<KSClassDeclaration>()
                .map {
                    SPIMetaInfoGenerator(it, environment, resolver).let {
                        it.check()
                        it.generateMetaInfo(skipWrite)
                    }
                }.toList()
        }
    }
    private val generateFileName = "${clazz.packageName.asString().replace(".", "_")}_${clazz.simpleName.asString()}"

    /**
     * 检查当前类合法性
     * 1. 是否存在公开无参构造函数
     * 2. 接口是否继承 IKmpService
     */
    fun check() {
        environment.logger.warn("[KmpSPI] start check ${clazz.qualifiedName?.asString()}")
        val isObject = clazz.classKind == ClassKind.OBJECT
        val isClass = clazz.classKind == ClassKind.CLASS
        val hasNoArgConstructor = clazz.getConstructors().any {
            it.parameters.size == 0 && it.getVisibility() == Visibility.PUBLIC
        }
        val className = clazz.qualifiedName?.asString()
        assert(isObject || (isClass && hasNoArgConstructor)) {
            "${className} must be object or has public no-arg constructor"
        }
        val serviceClass = getServiceClass()
        val serviceClassName = getServiceClassName()
        if (!serviceClass.implementsInterfaceRecursively(KMP_SPI_INTERFACE)) {
            error("SPI Interface must implement IKmpService: ${serviceClassName}")
        }
        if (!clazz.implementsInterfaceRecursively(serviceClassName)) {
            error("Class ${clazz.qualifiedName?.asString()} does not implement $serviceClass")
        }
    }

    fun generateMetaInfo(skipWrite: Boolean = false): SpiImplMetaInfo {
        val service = getServiceClassName()
        val info = SpiImplMetaInfo(service, clazz.qualifiedName!!.asString(), clazz.classKind == ClassKind.OBJECT)
        if (!skipWrite) {
            KspMetaInfoManager.saveMetaInfo(environment, SPI_IMPL_META_INFO_GENERATE_PACKAGE, generateFileName, SPI_IMPL_META_INFO_KEY, info)
        }

        return info
    }

    private fun getServiceClassName(): String {
        return getServiceClass().qualifiedName!!.asString()
    }

    private fun getServiceClass(): KSClassDeclaration {
        val annotation = clazz.annotations.first {
            it.annotationType.resolve().declaration
                .qualifiedName?.asString() == ANNOTATION_SPI_IMPL
        }
        val service = annotation.arguments.firstOrNull {
            it.name?.asString() == "service"
        }?.value
        return (service as KSType).declaration as KSClassDeclaration
    }

    private fun KSClassDeclaration.implementsInterfaceRecursively(targetInterface: String): Boolean {
        // 遍历所有直接父类型
        return this.superTypes.any { superType ->
            val resolvedType = superType.resolve()
            if (resolvedType.declaration.qualifiedName?.asString() == targetInterface) {
                return true
            }
            // 如果父类型是一个类或接口，递归检查
            val parentClass = resolvedType.declaration as? KSClassDeclaration
            parentClass?.implementsInterfaceRecursively(targetInterface) ?: false
        }
    }
}
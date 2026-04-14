    import java.util.*

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
//    id(libs.plugins.bytekmp.all.get().pluginId).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.kotlinx.parcelize).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    id(libs.plugins.bytekmp.all.get().pluginId).apply(false)
}

allprojects {
    repositories {
        mavenLocal()
        maven(url = "https://artifact.bytedance.com/repository/releases/")
        apply(from = "${rootDir}/build_properties.gradle")
        val buildProperties = extensions.findByName("build_properties") as Map<String, Any?>
        buildProperties["custom_maven_url"]?.let {
            maven(url = uri(it))
            logger.info("A custom maven repository ${it} was added")
        }
        mavenCentral()
        google()
    }
}

if (gradle.startParameter.taskNames.contains("publishAll")) {
    val version = project.findProperty("ARTIFACT_VERSION") as? String
    if (version == null) {
        throw GradleException("未找到 ARTIFACT_VERSION, 请添加 -DARTIFACT_VERSION=xxx.xxx.xxx.xxx 进行组件发布")
    }
    if(version == "9.9.9.9-local") {
        throw GradleException("未设置 ARTIFACT_VERSION, 请添加 -DARTIFACT_VERSION=xxx.xxx.xxx.xxx 进行组件发布")
    }
}

// 替换运行库为指定版本
//allprojects {
//    configurations.all {
//        if(isCanBeResolved) {
//            resolutionStrategy.eachDependency {
//                when {
//                    requested.module.group == "org.jetbrains.androidx.core" && requested.module.name.startsWith("core-bundle") -> this.useVersion("1.0.0-ohos-nd.137")
//                    // 这个请写在 compose 前面
//                    requested.module.group == "org.jetbrains.compose.components" && requested.module.name.startsWith("components-resources")  -> this.useVersion("1.6.10.ohos.nd.agp410.0")
//                    requested.module.group == "org.jetbrains.androidx.savedstate" && requested.module.name.startsWith("savedstate") -> this.useVersion("1.2.1-ohos-nd.137")
//                    requested.module.group == "com.bytedance.kmp" && requested.module.name.startsWith("krouter") -> this.useVersion("1.2.140")
//                    requested.module.group == "com.bytedance.kmp" && requested.module.name.startsWith("klay") -> this.useVersion("1.2.140")
//                    requested.module.group == "com.bytedance.kmp" && requested.module.name.startsWith("performance") -> this.useVersion("1.2.140")
//                    requested.module.group.startsWith("org.jetbrains.compose.") -> this.useVersion("1.6.10-ohos-nd.137")
//                    requested.module.group.startsWith("org.jetbrains.androidx.lifecycle.") -> this.useVersion("2.8.0-ohos-nd.137")
//                    requested.module.group.startsWith("org.jetbrains.androidx.navigation.") -> this.useVersion("2.7.7-ohos-nd.137")
//                }
//            }
//        }
//    }
//}

// 替换 maven 库为 project
allprojects {
    configurations.all {
        if(isCanBeResolved) {
            resolutionStrategy.dependencySubstitution {
                substitute(module("com.bytedance.kmp.spi:spi")).using(project(":runtime:spi:spi"))
                substitute(module("com.bytedance.kmp.ohos.ffi:library")).using(project(":runtime:ffi:library"))
                substitute(module("com.bytedance.kmp.ohos.ffi:annotation")).using(project(":runtime:ffi:annotation"))
                substitute(module("com.bytedance.kmp.compose.ohos:library")).using(project(":runtime:compose:library"))
                substitute(module("com.bytedance.kmp.compose.ohos:annotation")).using(project(":runtime:compose:annotation"))

                substitute(module("com.bytedance.kmp.compose.ohos:processor")).using(project(":processor:compose_processor"))
                substitute(module("com.bytedance.kmp.ohos.ffi:jsbind_generator")).using(project(":processor:jsbind_generator"))
                substitute(module("com.bytedance.kmp.ksp:meta-info-manager")).using(project(":processor:ksp_metainfo"))
                substitute(module("com.bytedance.kmp.spi:processor")).using(project(":processor:spi_processor"))
            }
        }
    }
}

tasks.register("publishAll") {
    group = "publishing"
    description = "Publish all artifacts to Maven repository"
    doLast {
        logger.lifecycle("Publish Finished. ")
    }

    // plugin
    dependsOn(gradle.includedBuild("gradle_plugin").task(":har_bundler:publish"))
    dependsOn(gradle.includedBuild("gradle_plugin").task(":publish:publish"))
    dependsOn(gradle.includedBuild("gradle_plugin").task(":bytekmp:publish"))

    // runtime
    dependsOn(":runtime:ffi:annotation:publish")
    dependsOn(":runtime:ffi:library:publish")
    dependsOn(":runtime:compose:annotation:publish")
    dependsOn(":runtime:compose:library:publish")
    dependsOn(":runtime:spi:spi:publish")
    dependsOn(":runtime:compose:performance:publish")

    // ksp
    dependsOn(":processor:ksp_metainfo:publish")
    dependsOn(":processor:jsbind_generator:publish")
    dependsOn(":processor:compose_processor:publish")
    dependsOn(":processor:spi_processor:publish")
}

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publish all artifacts to Maven Local repository"
    doLast {
        logger.lifecycle("Publish Finished. ")
    }

    // plugin
    dependsOn(gradle.includedBuild("gradle_plugin").task(":har_bundler:publishToMavenLocal"))
    dependsOn(gradle.includedBuild("gradle_plugin").task(":publish:publishToMavenLocal"))
    dependsOn(gradle.includedBuild("gradle_plugin").task(":bytekmp:publishToMavenLocal"))

    // runtime
    dependsOn(":runtime:ffi:annotation:publishToMavenLocal")
    dependsOn(":runtime:ffi:library:publishToMavenLocal")
    dependsOn(":runtime:compose:annotation:publishToMavenLocal")
    dependsOn(":runtime:compose:library:publishToMavenLocal")
    dependsOn(":runtime:spi:spi:publishToMavenLocal")
    dependsOn(":runtime:compose:performance:publishToMavenLocal")

    // ksp
    dependsOn(":processor:ksp_metainfo:publishToMavenLocal")
    dependsOn(":processor:jsbind_generator:publishToMavenLocal")
    dependsOn(":processor:compose_processor:publishToMavenLocal")
    dependsOn(":processor:spi_processor:publishToMavenLocal")
}

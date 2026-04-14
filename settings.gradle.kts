pluginManagement {
    includeBuild("gradle_plugin")
    repositories {
        mavenLocal()
        maven("https://artifact.bytedance.com/repository/releases/")
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

buildscript {
    includeBuild("gradle_plugin")
}

fun includeProject(path: String, dir: String? = null) {
    include(path)
    if(dir == null) return
    project(path).projectDir = rootDir.resolve(dir)
}

rootProject.name = "ByteKMPTools"

includeProject(":runtime")
includeProject(":runtime:ffi:annotation", "runtime/ffi_annotation")
includeProject(":runtime:ffi:library", "runtime/ffi_runtime")
includeProject(":runtime:compose:annotation", "runtime/compose_annotation")
includeProject(":runtime:compose:library", "runtime/compose_runtime")
includeProject(":runtime:compose:performance", "runtime/performance_runtime")
includeProject(":runtime:spi:spi", "runtime/spi_runtime")

includeProject(":processor")
includeProject(":processor:ksp_metainfo", "processor/ksp_metainfo")
includeProject(":processor:jsbind_generator", "processor/jsbind_generator")
includeProject(":processor:compose_processor", "processor/compose_processor")
includeProject(":processor:spi_processor", "processor/spi_processor")
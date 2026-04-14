plugins {
    val kotlinCompilerVersion = libs.versions.gradle.plugin.compiler.kotlin
    kotlin("jvm").version(kotlinCompilerVersion).apply(false)
    kotlin("plugin.serialization").version(kotlinCompilerVersion).apply(false)
}

// 透传 ../gradle.properties 中的值
val rootProperties = java.util.Properties().apply {
    load(rootDir.resolve("../gradle.properties").inputStream())
}

val propertiesNames = setOf<String>(
    "REPOSITORY",
    "REPOSITORY_SNAPSHOT",
    "UPLOAD_SOURCE",
    "ARTIFACT_VERSION",
    "custom_maven_url"
)
project.ext {
    rootProperties.forEach {
        if (propertiesNames.contains(it.key.toString())) {
            if(project.findProperty(it.key.toString()) == null) {
                set(it.key.toString(), it.value.toString())
            }
        }
    }
}

allprojects {
    repositories {
        mavenLocal()
        maven(url = "https://artifact.bytedance.com/repository/releases/")
        apply(from = "${rootDir}/../build_properties.gradle")
        val buildProperties = extensions.findByName("build_properties") as Map<String, Any?>
        buildProperties["custom_maven_url"]?.let {
            maven(url = uri(it))
            logger.info("A custom maven repository ${it} was added")
        }
        mavenCentral()
        google()
    }
}

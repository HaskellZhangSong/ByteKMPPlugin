pluginManagement {
    repositories {
        mavenLocal()
        maven("https://artifact.bytedance.com/repository/releases/")
        apply(from = "${rootDir}/../build_properties.gradle")
        val buildProperties = extensions.findByName("build_properties") as Map<String, Any?>
        buildProperties["custom_maven_url"]?.let {
            maven(url = uri(it))
            logger.info("A custom maven repository ${it} was added")
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven(url = "https://artifact.bytedance.com/repository/releases/")
        val buildProperties = extensions.findByName("build_properties") as Map<String, Any?>
        buildProperties["custom_maven_url"]?.let {
            maven(url = uri(it))
            logger.info("A custom maven repository ${it} was added")
        }
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "gradle_plugin"
include(":bytekmp")
include(":publish")
include(":har_bundler")

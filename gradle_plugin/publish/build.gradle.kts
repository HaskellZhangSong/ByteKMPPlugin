plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}


apply(from = "$rootDir/../publish.gradle")
apply(from = "$rootDir/../publish_configure_gradle_plugin.gradle")

gradlePlugin {
    plugins {
        create("bytekmp-publish") {
            id = libs.plugins.bytekmp.publish.get().pluginId
            implementationClass = "com.bytedance.kotlin.multiplatform.gradle.publish.PublishPlugin"
        }
    }
}

fun notation(pluginProvider: Provider<PluginDependency>): String {
    val plugin = pluginProvider.get()
    val group = plugin.pluginId
    val artifact = "${plugin.pluginId}.gradle.plugin"
    val versionConstraintAsString: String = plugin.version.toString()
    return if (versionConstraintAsString.isEmpty()) {
        "$group:$artifact"
    } else {
        "$group:$artifact:$versionConstraintAsString"
    }
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(notation(libs.plugins.kotlin.multiplatform))
    implementation(libs.jdom2)
}



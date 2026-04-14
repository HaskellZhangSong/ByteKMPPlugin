plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

apply(from = "$rootDir/../publish.gradle")
apply(from = "$rootDir/../publish_configure_gradle_plugin.gradle")

gradlePlugin {
    plugins {
        create("bundle") {
            id = libs.plugins.bytekmp.all.get().pluginId
            implementationClass = "com.bytedance.kotlin.multiplatform.KotlinPlugin"
        }
        create("bundle-meta") {
            id = libs.plugins.bytekmp.meta.all.get().pluginId
            implementationClass = "com.bytedance.kotlin.multiplatform.KotlinMetaPlugin"
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
    api(notation(libs.plugins.google.ksp))
    api(notation(libs.plugins.compose.plugin))
    compileOnly(notation(libs.plugins.compose.compiler))
    compileOnly(notation(libs.plugins.kotlinx.serialization))
    compileOnly(notation(libs.plugins.kotlinx.parcelize))
    compileOnly(notation(libs.plugins.android.library))
    implementation(project(":har_bundler"))
    implementation(project(":publish"))
}
val writeVersions by tasks.registering {
    val pluginVersion = project.findProperty("ARTIFACT_VERSION") as String
    val composeVersion = libs.versions.jetbrains.compose.get()
    val composeResourcesVersion = libs.versions.compose.components.resources.get()
    val lifecycleVersion = libs.versions.jetbrains.lifecycle.get()
    val navigationVersion = libs.versions.jetbrains.navigation.get()
    val savedstateVersion = libs.versions.jetbrains.savedstate.get()

    val versionFile = projectDir.resolve("src/main/kotlin/com/bytedance/kotlin/multiplatform/Versions.kt")
    inputs.property("pluginVersion", pluginVersion)
    inputs.property("composeVersion", composeVersion)
    inputs.property("composeResourcesVersion", composeResourcesVersion)
    inputs.property("lifecycleVersion", lifecycleVersion)
    inputs.property("navigationVersion", navigationVersion)
    inputs.property("savedstateVersion", savedstateVersion)
    outputs.file(versionFile)

    fun Task.replaceVersion(vararg replacement: Pair<String, String>) {
        check(versionFile.isFile) { "Version file $versionFile is not found" }
        var text = versionFile.readText()
        var changed = false

        replacement.forEach {
            val pattern = Regex(it.first)
            val newValue = it.second
            val match = pattern.find(text) ?: error("Version pattern is missing in file $versionFile")
            val group = match.groups[1]!!
            if (newValue != group.value) {
                logger.lifecycle("Writing new standard library version components: $newValue (was: ${group.value})")
                text = text.replaceRange(group.range, newValue)
                changed = true
            } else {
                logger.info("Standard library version components: ${group.value}")
            }
        }
        if(changed) {
            versionFile.writeText(text)
        }
    }

    doLast {
        replaceVersion(
            """const val CURRENT_VERSION = \"([\w|.|-]*)\"""" to pluginVersion,
            """const val COMPOSE_VERSION = \"([\w|.|-]*)\"""" to composeVersion,
            """const val COMPOSE_RESOURCES_VERSION = \"([\w|.|-]*)\"""" to composeResourcesVersion,
            """const val LIFECYCLE_VERSION = \"([\w|.|-]*)\"""" to lifecycleVersion,
            """const val NAVIGATION_VERSION = \"([\w|.|-]*)\"""" to navigationVersion,
            """const val SAVESTATE_VERSION = \"([\w|.|-]*)\"""" to savedstateVersion,
        )
    }
}
kotlin {
    sourceSets {
        val main by getting {
            kotlin.srcDirs(files("src/main/kotlin").builtBy(writeVersions))
        }
    }
}
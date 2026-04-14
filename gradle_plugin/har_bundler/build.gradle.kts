plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-gradle-plugin`
    `maven-publish`
}

apply(from = "$rootDir/../publish.gradle")
apply(from = "$rootDir/../publish_configure_gradle_plugin.gradle")

gradlePlugin {
    plugins {
        create("harBundle") {
            id = libs.plugins.bytekmp.har.bundler.get().pluginId
            implementationClass = "com.bytedance.kmp.ohos_ffi.har_bundle.SoHarGeneratorPlugin"
        }
    }
}

dependencies {
    implementation(gradleApi())
    compileOnly(notation(libs.plugins.kotlin.multiplatform))
    implementation(libs.kotlinx.serialization.json)
}

// 编译时压缩模板文件并挪到 resources 中
tasks.register<Zip>("zipTemplate") {
    group = "so har generate"
    from(project.projectDir)
    include("KmpTemplate/**/*.*")
    destinationDirectory.set(this.project.file("src/main/resources"))
    archiveFileName.set("KmpTemplate.zip")
}


tasks.named("processResources").configure {
    dependsOn("zipTemplate")
}

tasks.matching { it.name == "bitsUploadSource" }.configureEach {
    dependsOn("zipTemplate")
}

tasks.named("sourcesJar", Jar::class) {
    dependsOn("zipTemplate")
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

publishing {
    publications {
        this.withType<MavenPublication>().configureEach {
            if(name == "pluginMaven") {
                // 修改 maven 坐标
                group = project.property("ARTIFACT_GROUP").toString()
                artifactId = project.property("ARTIFACT_NAME").toString()
                version = project.property("ARTIFACT_VERSION").toString()
            }
        }
    }
}
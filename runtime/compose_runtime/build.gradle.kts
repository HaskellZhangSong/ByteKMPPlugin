import com.bytedance.kotlin.multiplatform.applyIfAbsent
import java.net.URI

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.bytekmp.all.get().pluginId)
    id(libs.plugins.bytekmp.publish.get().pluginId)
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.compose.compiler.get().pluginId)
}

apply(plugin = "com.google.devtools.ksp")
apply(plugin = "kotlin-composecompiler")

kotlin {

    androidTarget() {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
                languageVersion = "1.7"
                apiVersion = "1.7"
            }
        }
    }

    ohosArm64() {
        binaries {
            sharedLib {
                baseName = project.name
                freeCompilerArgs += listOf("-g")
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {

        all {
            languageSettings.languageVersion = "1.7"
            languageSettings.apiVersion = "1.7"
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }

        commonMain.dependencies {
            implementation(project(":runtime:ffi:annotation"))
            implementation(project(":runtime:ffi:library"))
            // compose
            implementation(libs.bundles.coreCompose)
        }

        nativeMain.dependencies {
            // native dependencies
        }
    }
}

android {
    namespace = "com.bytedance.kmp.compose.ohos.library"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

dependencies {
    add("kspOhosArm64", project(":processor:jsbind_generator"))
}

bytekmp_klib_publish {
    this.group = project.property("ARTIFACT_GROUP").toString()
    this.version = project.property("ARTIFACT_VERSION").toString()
}
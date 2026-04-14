@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.bytekmp.all.get().pluginId)
    id(libs.plugins.bytekmp.publish.get().pluginId)
}

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

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    )

    applyDefaultHierarchyTemplate()

    sourceSets {

        all {
            languageSettings.languageVersion = "1.7"
            languageSettings.apiVersion = "1.7"
        }

        commonMain.dependencies {
        }

        nativeMain.dependencies {
            // native dependencies
        }
        iosMain.dependencies {
//            implementation(libs.uikitarm64)
        }
    }
}

android {
    namespace = "com.bytedance.kmp.compose.ohos.annotation"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

bytekmp_klib_publish {
    this.group = project.property("ARTIFACT_GROUP").toString()
    this.version = project.property("ARTIFACT_VERSION").toString()
}
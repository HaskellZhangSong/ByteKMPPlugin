import java.net.URI

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.bytekmp.all.get().pluginId)
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.bytekmp.publish.get().pluginId)
}

apply(plugin = "com.google.devtools.ksp")

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
            compilations.all {
                cinterops {
                    val libffi_opt by creating {
                        defFile(project.file("src/ohosArm64Main/c/ffi_opt.def"))
                    }
                }
                // 将 so 打入 klib
                val dynamicLibs = listOf(project.file("src/ohosArm64Main/c/libffi_opt.so").absolutePath)
                compileTaskProvider {
                    compilerOptions.freeCompilerArgs.add("-include-binary=${dynamicLibs.joinToString(",")}")
                    val dynamicLibFiles = dynamicLibs.map { File(it) }
                    inputs.files(dynamicLibFiles).withPropertyName("includeBinary")
                }
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {

        all {
            languageSettings.languageVersion = "1.7"
            languageSettings.apiVersion = "1.7"
        }

        commonMain.dependencies {
            implementation(project(":runtime:ffi:annotation"))
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.datetime)
        }

        nativeMain.dependencies {
            // native dependencies
        }
    }
}

android {
    namespace = "com.bytedance.kmp.ohos.ffi.library"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

dependencies {
    add("kspOhosArm64", project(":processor:jsbind_generator"))
}

bytekmp_klib_publish {
    val publishGroup = project.findProperty("ARTIFACT_GROUP")
        ?: throw GradleException("Can't find ARTIFACT_GROUP in ${project.file("gradle.properties").absolutePath}")
    val publishVersion = project.findProperty("ARTIFACT_VERSION")
        ?: throw GradleException("Can't find ARTIFACT_VERSION in ${rootProject.file("gradle.properties").absolutePath}")
    this.group = publishGroup.toString()
    this.version = publishVersion.toString()
}
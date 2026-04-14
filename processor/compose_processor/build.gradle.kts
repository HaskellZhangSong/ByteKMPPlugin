plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinx.serialization)
    `maven-publish`
}

apply(from = "$rootDir/publish.gradle")
apply(from = "$rootDir/publish_configure_jar.gradle")

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ksp.api)
    implementation(project(":processor:jsbind_generator"))
    implementation(project(":processor:ksp_metainfo"))
}

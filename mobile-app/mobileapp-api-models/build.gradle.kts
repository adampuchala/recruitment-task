// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.openapi)
}

kotlin {
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.adampuchala.mobileapp.api"
        compileSdk = 36
        minSdk = 24
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }

        val kotlinxMain by creating {
            dependsOn(commonMain)
        }

        androidMain.get().dependsOn(kotlinxMain)
        iosMain.get().dependsOn(kotlinxMain)
    }
}

val openapiSpecPath = "$projectDir/../openapi.yaml"
val genDir = layout.buildDirectory.dir("generated/openapi")

val generateKotlinxModels = tasks.register<GenerateTask>("generateKotlinxModels") {
    generatorName.set("kotlin")
    library.set("multiplatform") 
    inputSpec.set(openapiSpecPath)
    outputDir.set(genDir.map { it.dir("kotlinx").asFile.absolutePath })
    
    modelPackage.set("com.adampuchala.mobileapp.api.model")
    
    globalProperties.set(mapOf(
        "models" to "",
        "apis" to "false",
        "supportingFiles" to "false"
    ))

    configOptions.set(mapOf(
        "enumPropertyNaming" to "UPPERCASE",
        "dateLibrary" to "kotlinx-datetime",
        "omitGradleWrapper" to "true"
        // NO serializationLibrary here!
    ))
}



// Map generated sources to Kotlin source sets with exclusions
kotlin.sourceSets.getByName("kotlinxMain").kotlin {
    srcDir(generateKotlinxModels.map { "${it.outputDir.get()}/src/commonMain/kotlin" })
    exclude("**/apis/**", "**/infrastructure/**")
}
// Fix dependency issue for Android Art Profile
tasks.configureEach {
    if (name == "prepareAndroidMainArtProfile") {
        dependsOn(generateKotlinxModels)
    }
}

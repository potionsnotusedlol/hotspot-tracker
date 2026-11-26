import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi)
}

val contractDirectory = layout.buildDirectory.dir("generated/openapi")
val contractFile = rootProject.file("../docs/openapi.yaml")

openApiValidate {
    inputSpec.set(contractFile.absolutePath)
}

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    inputSpec.set(contractFile.absolutePath)
    outputDir.set(contractDirectory.map { it.asFile.absolutePath })
    cleanupOutput.set(true)
    packageName.set("ru.moscow.lineaccess.api")
    apiPackage.set("ru.moscow.lineaccess.api")
    modelPackage.set("ru.moscow.lineaccess.api.model")
    // Координаты и оценка нагрузки передаются числами JSON.
    typeMappings.set(mapOf("number" to "kotlin.Double"))
    configOptions.set(mapOf(
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
        "useResponseAsReturnType" to "true",
        "dateLibrary" to "java8"
    ))
    globalProperties.set(mapOf(
        "apis" to "", "models" to "", "supportingFiles" to "CollectionFormats.kt",
        "apiDocs" to "false", "modelDocs" to "false",
        "apiTests" to "false", "modelTests" to "false"
    ))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    sourceSets.main { kotlin.srcDir(contractDirectory.map { it.dir("src/main/kotlin") }) }
}
tasks.named("compileKotlin") { dependsOn(tasks.openApiGenerate) }

dependencies {
    api(libs.retrofit.core)
    api(libs.serialization.json)
}

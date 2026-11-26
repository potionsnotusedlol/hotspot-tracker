import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

fun serverOrigin(value: String, localMock: Boolean): String {
    val uri = URI(value)
    val emulatorHttp = localMock && uri.scheme == "http" && uri.host in setOf("10.0.2.2", "127.0.0.1")
    require((uri.scheme == "https" || emulatorHttp) && uri.host != null && uri.userInfo == null &&
        uri.query == null && uri.fragment == null && uri.rawPath in listOf("", "/") &&
        (uri.port == -1 || uri.port in 1..65535)) {
        "Укажите HTTPS-адрес сервера без пути и учётных данных. HTTP разрешён для локального мока."
    }
    return uri.toASCIIString().trimEnd('/') + "/"
}

val devServer = providers.gradleProperty("lineaccess.devUrl").orElse("http://10.0.2.2:4010")
    .map { serverOrigin(it.trim(), localMock = true) }.get()
val prodServer = providers.gradleProperty("lineaccess.prodUrl")
    .orElse(providers.environmentVariable("LINEACCESS_PROD_URL"))
    .orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { serverOrigin(it, localMock = false) }.orEmpty()

android {
    namespace = "ru.moscow.lineaccess"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "ru.moscow.lineaccess"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            buildConfigField("String", "API_BASE_URL", "\"" + devServer + "\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"" + prodServer + "\"")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":api"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.retrofit.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.espresso)
    debugImplementation(libs.compose.ui.test.manifest)
}

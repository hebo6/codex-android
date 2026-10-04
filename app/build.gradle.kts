import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val mockitoAgent = configurations.create("mockitoAgent")

abstract class MockitoAgentProvider : CommandLineArgumentProvider {
    @get:Classpath
    abstract val agentJar: ConfigurableFileCollection

    override fun asArguments(): List<String> = listOf("-javaagent:${agentJar.asPath}")
}

tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(objects.newInstance<MockitoAgentProvider>().apply {
        agentJar.from(mockitoAgent)
    })
}

val releaseSigningEnabled = providers.gradleProperty("releaseSigning").orNull == "true"

fun requiredSigningEnvironmentVariable(name: String): String =
    providers.environmentVariable(name).orNull
        ?: error("$name must be set when releaseSigning=true")

android {
    namespace = "com.hebo.codex"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hebo.codex"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "0.1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningEnabled) {
            create("release") {
                storeFile = file(requiredSigningEnvironmentVariable("ANDROID_KEYSTORE_FILE"))
                storePassword = requiredSigningEnvironmentVariable("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = requiredSigningEnvironmentVariable("ANDROID_KEY_ALIAS")
                keyPassword = requiredSigningEnvironmentVariable("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.android.svg)
    implementation(libs.androidx.core)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.highlights)
    implementation(libs.haze)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.java.websocket)
    implementation(libs.jsch)
    implementation(libs.telephoto.subsampling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.material)
    implementation(libs.markdown.renderer.code)
    implementation(libs.markdown.renderer.m3)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    mockitoAgent(libs.mockito.core) { isTransitive = false }
    testImplementation(libs.kotlinx.coroutines.test)

    ksp(libs.androidx.room.compiler)
}

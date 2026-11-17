plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hypai.fion"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hypai.fion"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// Android matches resources by script, so values-zh (Hans) never serves zh-Hant systems such as zh-TW.
// Traditional Chinese systems get the Simplified Chinese strings through this generated mirror.
val mirrorChineseStrings = tasks.register<MirrorChineseStrings>("mirrorChineseStrings") {
    source = layout.projectDirectory.file("src/main/res/values-zh/strings.xml")
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(mirrorChineseStrings, MirrorChineseStrings::outputDir)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

abstract class MirrorChineseStrings : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun mirror() {
        val target = outputDir.file("values-b+zh+Hant/strings.xml").get().asFile
        target.parentFile.mkdirs()
        source.get().asFile.copyTo(target, overwrite = true)
    }
}

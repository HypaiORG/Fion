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
        versionCode = 6
        versionName = "1.0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeystore = providers.environmentVariable("FION_KEYSTORE_FILE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("FION_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("FION_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("FION_KEY_PASSWORD").get()
            }
        }
    }

    androidResources {
        localeFilters += listOf("en", "zh", "b+zh+Hant")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            vcsInfo {
                include = false
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs {
            excludes += setOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so",
            )
        }
        resources {
            excludes += setOf(
                "DebugProbesKt.bin",
                "META-INF/*.version",
                "META-INF/androidx/**",
                "kotlin/**",
            )
        }
    }
}

// Android matches resources by script, so values-zh (Hans) never serves zh-Hant systems such as zh-TW.
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
    implementation(libs.compose.material.ripple)
    implementation(libs.compose.material3.adaptive)
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
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
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

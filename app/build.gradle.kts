import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.android.application")
}

val appVersionName = "1.0.0"
val appVersionCode = 1

configure<com.android.build.api.dsl.ApplicationExtension> {
    namespace = "io.github.hohojia886.pixeltweaks"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.hohojia886.pixeltweaks"
        minSdk = 34
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("alt") {
            storeFile = file("../tmp/debug_alt.jks")
            storePassword = "password"
            keyAlias = "debug_alt"
            keyPassword = "password"
        }
    }

    flavorDimensions += "tier"
    productFlavors {
        create("lite") {
            dimension = "tier"
            buildConfigField("boolean", "ENABLE_CALL_RECORDING", "false")
        }
        create("full") {
            dimension = "tier"
            buildConfigField("boolean", "ENABLE_CALL_RECORDING", "true")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("debugAlt") {
            initWith(getByName("debug"))
            signingConfig = signingConfigs.getByName("alt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// APK naming and output path customization
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val flavor = variant.flavorName?.lowercase() ?: ""
            val buildType = variant.buildType?.lowercase() ?: ""
            @Suppress("UnstableApiUsage")
            output.outputFileName.set("pixel-tweaks-$flavor-v$appVersionName-$buildType.apk")
        }
    }
}


kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // libxposed API 102
    compileOnly("io.github.libxposed:api:102.0.0")
    testImplementation("io.github.libxposed:api:102.0.0")

    // Latest Stable Material 3 & AndroidX
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.activity:activity-ktx:1.13.0")

    // DexKit only for full version
    "fullImplementation"("org.luckypray:dexkit:2.2.0")

    // Unit tests (plain JVM - no emulator/Robolectric required)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Instrumentation tests (Emulator required)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

// --- 自動備份任務 ---
tasks.register<Zip>("backupProject") {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
    archiveFileName.set("PixelTweaks2_$timestamp.zip")
    destinationDirectory.set(file("C:/Users/Administrator/Documents/GitHub/Backups"))

    from(project.rootDir) {
        exclude("**/build/**")
        exclude("**/.gradle/**")
        exclude("**/.kotlin/**")
        exclude("**/.git/**")
        exclude("**/.idea/**")
        exclude("**/.artifacts/**")
        exclude("**/local.properties")
        exclude("**/tmp/**")
    }
}

// 讓所有以 Debug 結尾的 assemble 任務在完成後自動執行備份
tasks.matching { it.name.startsWith("assemble") && it.name.contains("Debug") }.configureEach {
    finalizedBy("backupProject")
}
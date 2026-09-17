import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = "1.0.7"
val appVersionCode = 8

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val releaseKeystoreFile = rootProject.file("app/keystore/release.jks")
val hasReleaseKeystore = releaseKeystoreFile.exists()

configure<ApplicationExtension> {
    namespace = "io.github.hohojia886.pixeltweaks"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.hohojia886.pixeltweaks"
        minSdk = 34
        targetSdk = 35
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
        compose = true
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = localProperties.getProperty("keystore.storePassword") ?: System.getenv("KEYSTORE_STORE_PASSWORD") ?: ""
                keyAlias = localProperties.getProperty("keystore.keyAlias") ?: System.getenv("KEYSTORE_KEY_ALIAS") ?: ""
                keyPassword = localProperties.getProperty("keystore.keyPassword") ?: System.getenv("KEYSTORE_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
            val buildType = variant.buildType?.lowercase() ?: ""
            @Suppress("UnstableApiUsage")
            output.outputFileName.set("pixel-tweaks-v$appVersionName-$buildType.apk")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // libxposed API 102
    compileOnly("io.github.libxposed:api:102.0.0")
    testImplementation("io.github.libxposed:api:102.0.0")

    // AndroidX & Material Design
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-ktx:1.13.0")

    // Jetpack Compose & Material 3
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Instrumentation tests
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

// --- Automated backup task ---
tasks.register<Zip>("backupProject") {
    description = "Creates a zip backup of project source code"
    group = "backup"
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

// Automatically trigger backup after any Debug assemble task completes
tasks.matching { it.name.startsWith("assemble") && it.name.contains("Debug") }.configureEach {
    finalizedBy("backupProject")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    android {
       namespace = "com.example.shiftword.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.sqldelight.androidDriver)
            // WindowCompat.getInsetsController, for dark mode's status/nav-bar icon appearance --
            // see StatusBarAppearance.android.kt.
            implementation(libs.androidx.core.ktx)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.sqldelight.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.navigation.compose)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.sqldelight.sqliteDriver)
                implementation(libs.compose.uiTest)
                implementation(libs.compose.uiTestJunit4)
                implementation(libs.robolectric)
            }
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

tasks.withType<Test> {
    // Robolectric (used by GridBoardSessionKeyTest) loads Conscrypt during Android environment
    // setup, which builds its native-library filename via a locale-sensitive lowercase of the OS
    // name. On a machine whose default JVM locale is Turkish, "Windows".lowercase() produces
    // "wındows" (dotless ı) instead of "windows", so the native lib lookup fails with
    // UnsatisfiedLinkError -- the classic JVM "Turkish locale" bug, unrelated to any of this
    // project's own code. Forcing the test JVM's default locale to en-US sidesteps it.
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")

    // Default JVM test-worker heap is too small for the 5x5 BFS/cascade-reachability
    // measurement tests (MoveLimitCalibrationTest, GeneratorMetricsTest) -- branching factor 20
    // at BFS_HARD_DEPTH_CAP=5 can enqueue millions of Grid states in a single worst-case call,
    // and resolveCascade's exhaustive reachability search calls BFS repeatedly on top of that.
    // This only affects the test runner process, never production code.
    maxHeapSize = "3g"
}

sqldelight {
    databases {
        create("WordShiftDatabase") {
            packageName.set("com.example.shiftword.db")
        }
    }
}
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the run number so every build installs over the previous one.
// Offset by 1000: the repo was recreated (run numbers restarted at 1), and builds
// from the old NobodyGenerator repo had higher version codes. Never lower this.
val versionCodeOffset = 1000
val buildNumber = versionCodeOffset + (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val channel = System.getenv("NOBODY_CHANNEL") ?: "release"
val baseVersion = "0.2.1"

val keystoreFile = file("nobody.jks")

// Offline neural voices (Kokoro) run on sherpa-onnx. Pinned to 1.13.8: the 2.0.x releases
// silently drop any word missing from Kokoro's dictionary (names, numbers), so don't bump
// without re-running the on-device Kokoro test. The AAR is fetched once and hash-checked.
val sherpaVersion = "1.13.8"
val sherpaSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")

fun sha256(f: File): String = MessageDigest.getInstance("SHA-256").let { md ->
    f.inputStream().use { input ->
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    md.digest().joinToString("") { "%02x".format(it) }
}

if (!sherpaAar.exists() || sha256(sherpaAar) != sherpaSha256) {
    sherpaAar.parentFile.mkdirs()
    val part = File(sherpaAar.path + ".part")
    val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
    logger.lifecycle("Downloading $url")
    URI(url).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
    val got = sha256(part)
    check(got == sherpaSha256) { "sherpa-onnx AAR hash mismatch: $got" }
    sherpaAar.delete()
    check(part.renameTo(sherpaAar)) { "Couldn't move ${part.name} into place" }
}

android {
    namespace = "com.faradaymicrosystems.nobodygenerator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.faradaymicrosystems.nobodygenerator"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = if (channel == "release") baseVersion else "$baseVersion-exp.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Phones only; x86_64 (emulator) is added for debug builds below.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("nobody") {
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("NOBODY_KS_PASS")
                keyAlias = "nobody"
                keyPassword = System.getenv("NOBODY_KS_PASS")
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                abiFilters += "x86_64"
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreFile.exists()) {
                signingConfigs.getByName("nobody")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Document reading
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("net.dankito.readability4j:readability4j:1.0.8")

    // Offline neural voices
    implementation(files(sherpaAar))
    implementation("org.apache.commons:commons-compress:1.27.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

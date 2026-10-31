import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: environment variables (CI) or the gitignored keystore.properties (local). Without them the release
// APK is left unsigned — never signed with a debug key.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun signingValue(env: String, prop: String): String? = System.getenv(env) ?: keystoreProps.getProperty(prop)

val releaseStoreFile = signingValue("SUFLYOR_KEYSTORE_FILE", "storeFile")

android {
    namespace = "com.olerast.suflyor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.olerast.suflyor"
        minSdk = 29
        targetSdk = 36
        versionCode = 13
        versionName = "1.4"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("SUFLYOR_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SUFLYOR_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("SUFLYOR_KEY_PASSWORD", "keyPassword")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release app, so testing never touches the real script library.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // The speech model is read straight from assets; keep it uncompressed so loading is fast.
        noCompress += listOf("onnx")
        // Only the app's own languages: library strings (Compose, Material) otherwise show up in a third language.
        localeFilters += listOf("en", "ru", "zh")
    }

    packaging {
        jniLibs {
            // Only the sherpa JNI API is used; its ONNX Runtime is statically linked and private.
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
    }

    // CI runs lintDebug: new problems fail the build, the ones known when lint was added sit in the baseline.
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkReleaseBuilds = false
    }

    // No encrypted dependency blob for Google in the APK: keeps builds reproducible and scanners quiet.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Keep speech's embedded runtime isolated from OCR's matching Java JNI + shared C runtime.
    implementation(files("libs/sherpa-onnx-arm64-1.13.8.aar"))
    implementation(files("libs/onnxruntime-android-1.24.3.aar"))
    implementation("org.opencv:opencv:4.12.0")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // 2026.06.01 = Compose 1.11 / Material3 1.4: the last line that builds with AGP 9.0 and compileSdk 36.
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// ---- Speech engine, English and Chinese models -------------------------------------------------------
// Not stored in git: downloaded once from their official sources, pinned to exact revisions and
// checked by SHA-256. Licenses: see THIRD_PARTY_NOTICES.md.

private val enModelBase =
    "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/672fbf1b30579d6585301139bb363f42a0ad4a24"

private val zhModelBase =
    "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/resolve/204ad334e2e683fd295359930cc16fc0432a23ac"

private val ocrModelBase =
    "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx"

private val speechAssets = listOf(
    Triple("libs/onnxruntime-android-1.24.3.aar",
        "https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.24.3/onnxruntime-android-1.24.3.aar",
        "67397e4a970e75617f765d2015ceaf911917e1d822276cfb5792744e8085cbce"),
    Triple("src/main/assets/rapidocr/det.onnx", "$ocrModelBase/PP-OCRv4/det/ch_PP-OCRv4_det_mobile.onnx", "d2a7720d45a54257208b1e13e36a8479894cb74155a5efe29462512d42f49da9"),
    Triple("src/main/assets/rapidocr/cls.onnx", "$ocrModelBase/PP-OCRv4/cls/ch_ppocr_mobile_v2.0_cls_mobile.onnx", "e47acedf663230f8863ff1ab0e64dd2d82b838fceb5957146dab185a89d6215c"),
    Triple("src/main/assets/rapidocr/rec-zh.onnx", "$ocrModelBase/PP-OCRv5/rec/ch_PP-OCRv5_rec_mobile.onnx", "5825fc7ebf84ae7a412be049820b4d86d77620f204a041697b0494669b1742c5"),
    Triple("src/main/assets/rapidocr/rec-en.onnx", "$ocrModelBase/PP-OCRv4/rec/en_PP-OCRv4_rec_mobile.onnx", "e8770c967605983d1570cdf5352041dfb68fa0c21664f49f47b155abd3e0e318"),
    Triple(
        "libs/sherpa-onnx-static-link-onnxruntime-1.13.8.aar",
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar",
        "b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471",
    ),
    // English: streaming Zipformer trained on LibriSpeech (icefall 2023-05-17, 320 ms chunks), int8 encoder and joiner.
    Triple("src/main/assets/asr-en/encoder.int8.onnx", "$enModelBase/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx", "563fde436d16cf7607cf408cd6b30909819d03162652ef389c2450ced3f45ac1"),
    Triple("src/main/assets/asr-en/decoder.onnx", "$enModelBase/decoder-epoch-99-avg-1-chunk-16-left-128.onnx", "7bf787f90b194b307e5a4ad6a34fadb4e748304c35f78a8d66358a05b13ee6ef"),
    Triple("src/main/assets/asr-en/joiner.int8.onnx", "$enModelBase/joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx", "d944208d660d67c8d72cd2acaeac971fa5ceb8c80e76c1968148846fedd6e297"),
    Triple("src/main/assets/asr-en/tokens.txt", "$enModelBase/tokens.txt", "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb"),
    // The same LibriSpeech BPE-500 vocabulary (its model file equals the one in the repo above), for script-word hints.
    Triple(
        "src/main/assets/asr-en/bpe.vocab",
        "https://huggingface.co/csukuangfj/icefall-asr-librispeech-conformer-ctc-jit-bpe-500-2021-11-09/resolve/66448a2164b7a67cc4d2118a2f1b2709d9fde148/data/lang_bpe_500/unigram_500.vocab",
        "28c02989b3cd8c2ffa974b1e33f97ec6cded170bda622ca627b7330b41c6c827",
    ),
    // Chinese: WenetSpeech 14M streaming Zipformer, character tokens (no BPE vocabulary).
    Triple("src/main/assets/asr-zh/encoder.int8.onnx", "$zhModelBase/encoder-epoch-99-avg-1.int8.onnx", "1c556ea57cec304e55ec4b72e52c1cc098bb01476ed7d90f3de939fe126487b1"),
    Triple("src/main/assets/asr-zh/decoder.onnx", "$zhModelBase/decoder-epoch-99-avg-1.onnx", "5ee0f03a2768ff1d5c83ef3a493243c7935d316cd41280037b14783a3467cc78"),
    Triple("src/main/assets/asr-zh/joiner.int8.onnx", "$zhModelBase/joiner-epoch-99-avg-1.int8.onnx", "a7cf9d82757bdcf786059454495a9ca95e4bd7347f72473fc08d794475c36169"),
    Triple("src/main/assets/asr-zh/tokens.txt", "$zhModelBase/tokens.txt", "8b294db9045d6e5f94647f4c1eec1af4da143a75053c399611444b378ff966ac"),
)

private fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

private fun download(url: String, to: File) {
    var lastError: Exception? = null
    repeat(3) { attempt ->
        try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.inputStream.use { input -> to.outputStream().use { input.copyTo(it) } }
            return
        } catch (e: Exception) {
            lastError = e
            if (attempt < 2) Thread.sleep(2_000L * (attempt + 1))
        }
    }
    throw GradleException("Could not download $url", lastError)
}

// Read a length-delimited protobuf field without a build-time Python/ONNX dependency.
private fun protobufField(bytes: ByteArray, wanted: Int): List<ByteArray> {
    var position = 0
    fun varint(): Long {
        var value = 0L
        for (shift in 0..63 step 7) {
            require(position < bytes.size)
            val b = bytes[position++].toInt() and 255
            value = value or ((b and 127).toLong() shl shift)
            if (b and 128 == 0) return value
        }
        error("Invalid ONNX protobuf")
    }
    val found = mutableListOf<ByteArray>()
    while (position < bytes.size) {
        val tag = varint().toInt()
        when (tag and 7) {
            0 -> varint()
            1 -> position += 8
            2 -> {
                val length = varint().toInt()
                require(length >= 0 && length <= bytes.size - position)
                if (tag ushr 3 == wanted) found += bytes.copyOfRange(position, position + length)
                position += length
            }
            5 -> position += 4
            else -> error("Unsupported ONNX protobuf wire type")
        }
        require(position <= bytes.size)
    }
    return found
}

val fetchSpeechAssets by tasks.registering {
    group = "build setup"
    description = "Downloads offline speech/OCR engines and bundled Chinese/English models, verified by SHA-256."
    val dir = layout.projectDirectory.asFile
    doLast {
        // Remove obsolete downloaded assets from existing checkouts before packaging.
        delete(File(dir, "src/main/assets/asr-ru"))
        delete(File(dir, "src/main/assets/ocr"))
        delete(File(dir, "libs/tesseract4android-4.9.0.aar"))
        delete(File(dir, "libs/sherpa-onnx-1.13.8.aar"))
        delete(File(dir, "libs/onnxruntime4j-1.24.3.aar"))
        delete(File(dir, "libs/onnxruntime-android-1.24.3-source.aar"))
        for ((path, url, sha) in speechAssets) {
            val target = File(dir, path)
            if (target.exists() && sha256(target) == sha) continue
            target.parentFile.mkdirs()
            logger.lifecycle("Downloading $url")
            val part = File(target.parentFile, target.name + ".part")
            download(url, part)
            val got = sha256(part)
            if (got != sha) {
                part.delete()
                throw GradleException("Checksum mismatch for $path: expected $sha, got $got")
            }
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        // The official static AAR still contains shared-runtime builds for unused x86 ABIs.
        // AGP merges those before abiFilters, so keep only the verified ARM64 static JNI build.
        val source = File(dir, "libs/sherpa-onnx-static-link-onnxruntime-1.13.8.aar")
        val arm64 = File(dir, "libs/sherpa-onnx-arm64-1.13.8.aar")
        ZipInputStream(source.inputStream()).use { input ->
            ZipOutputStream(arm64.outputStream()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.name.startsWith("jni/") && !entry.name.startsWith("jni/arm64-v8a/")) continue
                    output.putNextEntry(ZipEntry(entry.name).apply { time = 0 })
                    input.copyTo(output)
                    output.closeEntry()
                }
            }
        }
        // Extract full UTF-8 dictionaries directly from the verified ONNX metadata.
        // JNI's modified UTF-8 conversion loses supplementary characters in the PP-OCRv5 dictionary.
        for (language in listOf("zh", "en")) {
            val model = File(dir, "src/main/assets/rapidocr/rec-$language.onnx")
            val dictionary = protobufField(model.readBytes(), 14).firstNotNullOfOrNull { entry ->
                val key = protobufField(entry, 1).firstOrNull()?.toString(Charsets.UTF_8)
                if (key == "character") protobufField(entry, 2).firstOrNull()?.toString(Charsets.UTF_8) else null
            } ?: throw GradleException("Missing OCR dictionary for $language")
            File(dir, "src/main/assets/rapidocr/keys-$language.txt").writeText(dictionary, Charsets.UTF_8)
        }
    }
}

tasks.named("preBuild") { dependsOn(fetchSpeechAssets) }

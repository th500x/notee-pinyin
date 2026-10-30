import java.util.Properties

plugins {
    id("com.android.application")
}

val appVersionName = "0.1.0"
val appVersionCode = 1

// GPL-3.0 source address for the setup page; the same address goes into the store listing.
val sourceUrl = providers.gradleProperty("notee.pinyin.sourceUrl").orElse("").get().trim()

// Upload key for Play. keystore.properties and the .jks stay outside git.
val uploadKey = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

android {
    namespace = "com.notee.pinyin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.notee.pinyin"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        val urlLiteral = sourceUrl.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "SOURCE_URL", "\"$urlLiteral\"")
    }

    signingConfigs {
        if (!uploadKey.isEmpty) {
            create("upload") {
                storeFile = file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (!uploadKey.isEmpty) {
                signingConfig = signingConfigs.getByName("upload")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // jvmTarget follows targetCompatibility under AGP 9 built-in Kotlin.
    buildFeatures {
        buildConfig = true
    }
    androidResources {
        // The compiled tables are memory-mapped straight out of the APK.
        noCompress += "bin"
    }
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

/** Runs the `:core` dictionary compiler: rime-ice + pins + OpenCC + English → `lexicon.bin`, `s2t.bin`, `english.bin`. */
abstract class CompileDictionaries : JavaExec() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dictDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    override fun exec() {
        outputDir.get().asFile.deleteRecursively()
        args(dictDir.get().asFile.absolutePath, outputDir.get().asFile.absolutePath)
        super.exec()
    }
}

val dictCompiler: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}
dependencies {
    dictCompiler(project(":core"))
}

val compileDictionaries = tasks.register<CompileDictionaries>("compileDictionaries") {
    group = "build"
    description = "Compile the vendored dictionaries into the binary tables the keyboard maps."
    classpath = dictCompiler
    mainClass.set("com.notee.pinyin.dict.DictSourcesKt")
    dictDir.set(rootProject.layout.projectDirectory.dir("dict"))
    outputDir.set(layout.buildDirectory.dir("generated/dictAssets"))
    maxHeapSize = "2g"
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(compileDictionaries, CompileDictionaries::outputDir)
    }
}

val checkReleaseSourceUrl = tasks.register("checkReleaseSourceUrl") {
    group = "verification"
    description = "GPL-3.0: a release must name where its source is."
    val url = sourceUrl
    doLast {
        check(url.isNotEmpty()) { "Set notee.pinyin.sourceUrl in gradle.properties before a release build." }
    }
}

tasks.register("copyDebugApkToRepoRoot") {
    group = "build"
    description = "Copy app-debug.apk to the Notee Pinyin repository root."
    doLast {
        val apk = layout.buildDirectory.get().asFile.resolve("outputs/apk/debug/app-debug.apk")
        check(apk.isFile) { "Debug APK missing: ${apk.absolutePath}" }
        val dest = rootProject.projectDir.resolve("app-debug.apk")
        apk.copyTo(dest, overwrite = true)
    }
}

tasks.register("trimAppBuildKeepOutputs") {
    group = "build"
    description = "Delete the off-tree app build dir except outputs/."
    doLast {
        val root = layout.buildDirectory.get().asFile
        if (!root.isDirectory) return@doLast
        root.listFiles()?.forEach { child ->
            if (!child.name.equals("outputs", ignoreCase = true)) {
                child.deleteRecursively()
            }
        }
    }
}

afterEvaluate {
    tasks.findByName("preReleaseBuild")?.dependsOn(checkReleaseSourceUrl)
    tasks.findByName("assembleDebug")?.finalizedBy(
        "copyDebugApkToRepoRoot",
        "trimAppBuildKeepOutputs"
    )
    tasks.findByName("assembleRelease")?.finalizedBy("trimAppBuildKeepOutputs")
    tasks.named("trimAppBuildKeepOutputs") {
        mustRunAfter("copyDebugApkToRepoRoot")
    }
}

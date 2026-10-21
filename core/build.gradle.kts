import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // Engine tests compile the real dictionaries once, so they need the repo root and some heap.
    systemProperty("notee.repoDir", rootProject.projectDir.absolutePath)
    systemProperty("notee.testAssetDir", layout.buildDirectory.dir("test-assets").get().asFile.absolutePath)
    maxHeapSize = "2g"
}

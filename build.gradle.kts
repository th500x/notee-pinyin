plugins {
    id("com.android.application") version "9.1.1" apply false
    // AGP 9 compiles Android Kotlin itself; this pins the same Kotlin for the JVM `core` module.
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
}

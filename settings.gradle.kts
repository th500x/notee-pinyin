pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NoteePinyin"
include(":core", ":app")

// Off-tree Gradle dir: `{same-drive}:\notee-pinyin-build`. Never Go's `notee-build` or Lrc's `notee-lrc-build`.
val localFile = file("local.properties")
val props = java.util.Properties()
if (localFile.isFile) {
    localFile.inputStream().use { props.load(it) }
}
val configured = props.getProperty("build.dir")?.trim().orEmpty()
val rootPath = rootDir.absolutePath
val sameDriveBuild = if (rootPath.length >= 2 && rootPath[1] == ':') {
    "${rootPath[0]}:\\notee-pinyin-build"
} else {
    ""
}
val offTree = configured.ifEmpty { sameDriveBuild }
if (offTree.isNotEmpty()) {
    gradle.allprojects {
        layout.buildDirectory.set(file("$offTree/$name"))
    }
}

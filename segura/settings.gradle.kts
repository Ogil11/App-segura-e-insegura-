pluginManagement {
    repositories {
        google()
        // Espejo oficial de Google de Maven Central (por si repo.maven.apache.org
        // responde 403/límite de peticiones en la red de la universidad).
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "MiBancoSegura"
include(":app")

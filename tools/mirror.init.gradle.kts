// Maven Central answers 403 on this network: send its traffic to Google's mirror.
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
fun RepositoryHandler.fix() = all {
    if (this is MavenArtifactRepository && url.toString().contains("repo.maven.apache.org")) setUrl(mirror)
}
settingsEvaluated {
    pluginManagement.repositories.fix()
    pluginManagement.repositories.maven(mirror)
}
allprojects {
    buildscript.repositories.fix()
    repositories.fix()
    repositories.maven(mirror)
}

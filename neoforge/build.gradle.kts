plugins {
    java
    id("net.neoforged.moddev") version ("2.0.148")
}

version = property("mod_version")!!
group = "com.hbm.airdefense"
base.archivesName = "ntm-air-defense-neoforge"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

sourceSets.main {
    java.srcDir("../common/src/main/java")
    resources.srcDir("../common/src/main/resources")
}

neoForge {
    version = property("neoforge_version").toString()
}

repositories {
    maven("https://repo.warfactory.co/releases") {
        content { includeGroup("com.hbm"); includeGroup("dev.engine_room") }
    }
}

dependencies {
    compileOnly("com.hbm:ntm-next-neoforge:${property("ntm_version")}")
    compileOnly("dev.engine_room:crankshaft-neoforge:${property("crankshaft_version")}")
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("META-INF/neoforge.mods.toml") { expand("version" to version) }
}

plugins {
    java
    id("net.fabricmc.fabric-loom") version ("1.18.2")
}

version = property("mod_version")!!
group = "com.hbm.airdefense"
base.archivesName = "ntm-air-defense-fabric"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

sourceSets.main {
    java.srcDir("../common/src/main/java")
    resources.srcDir("../common/src/main/resources")
}

repositories {
    maven("https://repo.warfactory.co/releases") {
        content { includeGroup("com.hbm"); includeGroup("dev.engine_room") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    compileOnly("com.hbm:ntm-next-fabric:${property("ntm_version")}")
    // NTM's turret renderer implements a Flywheel (Crankshaft) interface.
    compileOnly("dev.engine_room:crankshaft-fabric:${property("crankshaft_version")}")
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") { expand("version" to version) }
}

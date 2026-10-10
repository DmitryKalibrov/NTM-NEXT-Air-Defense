plugins {
    java
    id("net.fabricmc.fabric-loom") version ("1.18.2")
}

version = property("mod_version")!!
group = "com.hbm.airdefense"
base.archivesName = "ntm-air-defense"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    // NTM-NEXT is not published to a maven; drop its jar (from your mods folder) into libs/.
    compileOnly(fileTree("libs") { include("*.jar") })
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") { expand("version" to version) }
}

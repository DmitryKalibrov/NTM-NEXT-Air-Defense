plugins {
    java
    id("net.fabricmc.fabric-loom") version ("1.18.2")
}

// NTM 1.0.3 replaced the multiblock shape API with baked layouts (common/src/ntm103); -Pntm=102 builds for NTM 1.0.2
val ntm = (findProperty("ntm") ?: "103").toString()
version = property("mod_version").toString() + if (ntm == "102") "+ntm1.0.2" else ""
group = "com.hbm.airdefense"
base.archivesName = "ntm-air-defense-fabric"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

sourceSets.main {
    java.srcDir("../common/src/main/java")
    java.srcDir("../common/src/ntm$ntm/java")
    resources.srcDir("../common/src/main/resources")
    resources.srcDir("../common/src/ntm$ntm/resources")
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
    val legacy = ntm == "102"
    compileOnly("com.hbm:ntm-next-fabric:${property(if (legacy) "ntm_legacy_version" else "ntm_version")}")
    // NTM's turret renderer implements a Flywheel (Crankshaft) interface.
    compileOnly("dev.engine_room:crankshaft-fabric:${property(if (legacy) "crankshaft_legacy_version" else "crankshaft_version")}")
}

tasks.processResources {
    val hbm = if (ntm == "102") ">=1.0.2 <1.0.3-" else ">=1.0.3-"
    inputs.property("version", version)
    inputs.property("hbm", hbm)
    filesMatching("fabric.mod.json") { expand("version" to version, "hbm" to hbm) }
}

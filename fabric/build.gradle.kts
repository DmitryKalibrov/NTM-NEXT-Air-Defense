plugins {
    java
    id("net.fabricmc.fabric-loom") version ("1.18.2")
}

// -Pntm=110 builds against the NTM 1.1.0 test builds in ../ntm-test-builds (multiblock layouts built at runtime)
val ntm = (findProperty("ntm") ?: "102").toString()
version = property("mod_version").toString() + if (ntm == "110") "+ntm1.1" else ""
group = "com.hbm.airdefense"
base.archivesName = "ntm-air-defense-fabric"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

sourceSets.main {
    java.srcDir("../common/src/main/java")
    java.srcDir("../common/src/ntm$ntm/java")
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
    if (ntm == "110") {
        compileOnly(files("../../ntm-test-builds/hbm-fabric-1.1.0.jar", "../../ntm-test-builds/crankshaft-fabric-1.6.0+mc26.2.jar"))
    } else {
        compileOnly("com.hbm:ntm-next-fabric:${property("ntm_version")}")
    // NTM's turret renderer implements a Flywheel (Crankshaft) interface.
        compileOnly("dev.engine_room:crankshaft-fabric:${property("crankshaft_version")}")
    }
}

tasks.processResources {
    val hbm = if (ntm == "110") ">=1.1.0-" else ">=1.0.2 <1.1.0-"
    inputs.property("version", version)
    inputs.property("hbm", hbm)
    filesMatching("fabric.mod.json") { expand("version" to version, "hbm" to hbm) }
}

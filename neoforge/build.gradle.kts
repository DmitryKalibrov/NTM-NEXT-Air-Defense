plugins {
    java
    id("net.neoforged.moddev") version ("2.0.148")
}

// -Pntm=110 builds against the NTM 1.1.0 test builds in ../ntm-test-builds (multiblock layouts built at runtime)
val ntm = (findProperty("ntm") ?: "102").toString()
version = property("mod_version").toString() + if (ntm == "110") "+ntm1.1" else ""
group = "com.hbm.airdefense"
base.archivesName = "ntm-air-defense-neoforge"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

sourceSets.main {
    java.srcDir("../common/src/main/java")
    java.srcDir("../common/src/ntm$ntm/java")
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
    if (ntm == "110") {
        compileOnly(files("../../ntm-test-builds/hbm-neoforge-1.1.0.jar", "../../ntm-test-builds/crankshaft-neoforge-1.6.0+mc26.2.jar"))
    } else {
        compileOnly("com.hbm:ntm-next-neoforge:${property("ntm_version")}")
        compileOnly("dev.engine_room:crankshaft-neoforge:${property("crankshaft_version")}")
    }
}

tasks.processResources {
    val hbm = if (ntm == "110") "[1.1.0,)" else "[1.0.2,1.1.0)"
    inputs.property("version", version)
    inputs.property("hbm", hbm)
    filesMatching("META-INF/neoforge.mods.toml") { expand("version" to version, "hbm" to hbm) }
}

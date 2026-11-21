plugins {
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "quarkos_cosmetics-fabric"

val unobfuscated = sc.current.parsed >= "26.1"
val javaVersion = if (unobfuscated) 25 else 21
val geckolibGroup = sc.properties.get<String>("deps.geckolib_group")
val geckolibVersion = sc.properties.get<String>("deps.geckolib")

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository { maven("https://repo.unascribed.com") { name = "Unascribed" } }
        filter { includeGroup("com.unascribed") }
    }
    exclusiveContent {
        forRepository { maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") { name = "GeckoLib" } }
        filter { includeGroup(geckolibGroup) }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Loom Back Compat skips mappings and uses plain dependency configurations on 26.x.
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")
    modCompileOnly("maven.modrinth:modmenu:${sc.properties.get<String>("deps.modmenu")}")
    compileOnly("com.unascribed:ears-api:1.4.5")
    modImplementation("$geckolibGroup:geckolib-fabric-${sc.current.version}:$geckolibVersion")

    val jackson = "2.18.3"
    modImplementation("com.fasterxml.jackson.core:jackson-databind:$jackson")
    modImplementation("com.fasterxml.jackson.core:jackson-core:$jackson")
    modImplementation("com.fasterxml.jackson.core:jackson-annotations:$jackson")
    include("com.fasterxml.jackson.core:jackson-databind:$jackson")
    include("com.fasterxml.jackson.core:jackson-core:$jackson")
    include("com.fasterxml.jackson.core:jackson-annotations:$jackson")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.11.0")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        val releaseGameDir = providers.gradleProperty("releaseGameDir").orNull
        runDirectory = if (releaseGameDir != null) {
            rootProject.file(releaseGameDir)
        } else if (sc.current.version == "1.21.11") {
            rootProject.file("run")
        } else {
            rootProject.file("run/${sc.current.version}")
        }
    }
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

val chiseledMain = layout.buildDirectory.dir("generated/stonecutter/main")
gradle.projectsEvaluated {
    val main = sourceSets.named("main").get()
    val generated = chiseledMain.get()
    main.java.setSrcDirs(listOf(generated.dir("java").asFile))
    main.resources.setSrcDirs(listOf(generated.dir("resources").asFile))
}
tasks.named("compileJava") { dependsOn("stonecutterGenerate") }
tasks.named("processResources") { dependsOn("stonecutterGenerate") }
tasks.named("compileTestJava") { dependsOn("stonecutterGenerate") }

val writePack = tasks.register("writePackMeta") {
    val out = layout.buildDirectory.file("generated/pack/pack.mcmeta")
    outputs.file(out)
    val meta = when {
        sc.current.version == "26.3" -> """{"pack":{"description":"Quark's Cosmetics","min_format":[97,1],"max_format":[97,1]}}"""
        sc.current.parsed >= "1.21.9" -> """{"pack":{"description":"Quark's Cosmetics","min_format":[75,0],"max_format":[75,0]}}"""
        else -> """{"pack":{"description":"Quark's Cosmetics","pack_format":34}}"""
    }
    inputs.property("packMetadata", meta)
    doLast {
        val file = out.get().asFile
        file.parentFile.mkdirs()
        file.writeText(meta + "\n")
    }
}

tasks {
    processResources {
        dependsOn(writePack)
        val minecraftVersion = sc.current.version
        val props = mapOf(
            "id" to sc.properties.get<String>("mod.id"),
            "name" to sc.properties.get<String>("mod.name"),
            "version" to "${sc.properties.get<String>("mod.version")}+$minecraftVersion",
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "java" to ">=$javaVersion",
            "geckolib" to geckolibVersion,
        )
        inputs.properties(props)
        exclude("META-INF/mods.toml")
        filesMatching("fabric.mod.json") {
            expand(props)
        }
        if (unobfuscated) {
            filesMatching("quarkos_cosmetics.mixins.json") {
                filter { line: String -> line.replace("\"JAVA_21\"", "\"JAVA_$javaVersion\"") }
            }
        }
        from(layout.buildDirectory.dir("generated/pack"))
    }

    test {
        useJUnitPlatform()
        if (providers.gradleProperty("liveGallery").orNull == "true") {
            systemProperty("quarkos.liveGallery", "true")
        }
    }

    withType<JavaExec>().configureEach {
        val shots = providers.gradleProperty("shots").orNull
            ?: "/cursor/stores/bc-1243ce51-36c9-58c7-8fa4-153844455530/media/fabric"
        systemProperty("quarkos_cosmetics.shots", shots)
        systemProperty("quarkos_cosmetics.loader", "fabric")
        systemProperty("cosmetics.galleryUrl", "http://127.0.0.1:39221")
        if (providers.gradleProperty("harness").orNull == "true") {
            systemProperty("quarkos_cosmetics.harness", "true")
            val wide = providers.gradleProperty("wide").orNull == "true"
            args("--width", if (wide) "1920" else "960", "--height", if (wide) "1080" else "540")
            // Same offline player as the other head shots, so the default skin does not change.
            args("--username", "Player546")
        }
        providers.gradleProperty("releasePlan").orNull?.let {
            systemProperty("quarkos_cosmetics.releasePlan", it)
        }
        if (providers.gradleProperty("wardrobeOnly").orNull == "true") {
            systemProperty("quarkos_cosmetics.wardrobeOnly", "true")
        }
        val only = providers.gradleProperty("only").orNull
        if (!only.isNullOrBlank()) {
            systemProperty("quarkos_cosmetics.only", only)
        }
        val display = providers.gradleProperty("display").orNull
        val xauth = providers.gradleProperty("xauth").orNull
        if (!display.isNullOrBlank()) {
            environment("DISPLAY", display)
        }
        if (!xauth.isNullOrBlank()) {
            environment("XAUTHORITY", xauth)
        }
    }

    named<JavaExec>("runClient") {
        jvmArgs("-Xmx1536M")
        doFirst {
            val eula = workingDir.resolve("eula.txt")
            eula.parentFile?.mkdirs()
            eula.writeText("eula=true\n")
        }
    }
}

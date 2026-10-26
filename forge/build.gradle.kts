buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.fabricmc.net/")
    }
    dependencies {
        classpath("net.minecraftforge.gradle:net.minecraftforge.gradle.gradle.plugin:7.0.40")
    }
}

data class ForgeNode(
    val forgeCoordinate: String,
    val forgeDep: String,
    val geckolibVersion: String,
    val packMeta: String,
)

val minecraftVersion = project.name
val forgeNode = when (minecraftVersion) {
    "1.21.1" -> ForgeNode(
        "1.21.1-52.1.20",
        "52.1.20",
        "4.9.3",
        """{"pack":{"description":"Quarkos Cosmetics","pack_format":34}}""" + "\n",
    )
    "1.21.11" -> ForgeNode(
        "1.21.11-61.2.4",
        "61.2.4",
        "5.4.5",
        """{"pack":{"description":"Quarkos Cosmetics","min_format":[75,0],"max_format":[75,0]}}""" + "\n",
    )
    else -> null
}

if (minecraftVersion == "forge" || forgeNode == null) {
    tasks.configureEach {
        if (name != "build") {
            enabled = false
        }
    }
    tasks.named("build") {
        enabled = true
        setDependsOn(emptyList<Any>())
        doLast {
            if (forgeNode == null && minecraftVersion != "forge") {
                throw GradleException("No Forge coordinate is configured for Minecraft $minecraftVersion.")
            }
        }
    }
} else {
    val node = forgeNode
    apply(plugin = "java")
    apply(plugin = "net.minecraftforge.gradle")

    version = "1.0.0+$minecraftVersion"
    base.archivesName.set("quarkos_cosmetics-forge")
    group = "dev.quarkos"

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    val minecraftExt = extensions.getByName("minecraft")
    val fg = extensions.getByName("fg")
    val forgeMinecraft = minecraftExt as net.minecraftforge.gradle.MinecraftExtensionForProject
    forgeMinecraft.mappings("official", minecraftVersion)
    val runs = (minecraftExt as groovy.lang.GroovyObject).getProperty("runs")
        as org.gradle.api.NamedDomainObjectContainer<net.minecraftforge.gradle.SlimeLauncherOptions>
    val runDir = rootProject.layout.projectDirectory.dir("run/forge/$minecraftVersion")
    runs.configureEach {
        workingDir.set(runDir)
        systemProperty("forge.logging.console.level", "info")
    }
    val sourceSets = extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
    val mainSource = sourceSets.named("main")
    val chiseledMain = layout.buildDirectory.dir("generated/stonecutter/main")
    val syncBranchSources = tasks.register<Sync>("syncBranchSources") {
        from(rootProject.file("src"))
        into(rootProject.file("forge/src"))
    }
    tasks.named("stonecutterPrepare") { dependsOn(syncBranchSources) }
    gradle.projectsEvaluated {
        val main = mainSource.get()
        main.java.setSrcDirs(listOf(chiseledMain.get().dir("java").asFile))
        main.resources.setSrcDirs(listOf(chiseledMain.get().dir("resources").asFile))
        main.output.setResourcesDir(main.java.destinationDirectory)
    }
    tasks.named("compileJava") { dependsOn("stonecutterGenerate") }
    tasks.named("processResources") { dependsOn("stonecutterGenerate") }
    runs.register("client") {
        mods {
            register("quarkos_cosmetics") {
                source(mainSource.get())
            }
        }
    }

    repositories {
        forgeMinecraft.mavenizer(this)
        maven((fg as net.minecraftforge.gradle.ForgeGradleExtension).forgeMaven)
        maven((fg as net.minecraftforge.gradle.ForgeGradleExtension).minecraftLibsMaven)
        maven("https://maven.minecraftforge.net/")
        mavenCentral()
        exclusiveContent {
            forRepository { maven("https://repo.unascribed.com") { name = "Unascribed" } }
            filter { includeGroup("com.unascribed") }
        }
        exclusiveContent {
            forRepository { maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") { name = "GeckoLib" } }
            filter { includeGroup("software.bernie.geckolib") }
        }
    }

    dependencies {
        add("implementation", forgeMinecraft.dependency("net.minecraftforge:forge:${node.forgeCoordinate}"))
        add("compileOnly", "com.unascribed:ears-api:1.4.5")
        add("implementation", "com.fasterxml.jackson.core:jackson-databind:2.18.3")
        add("implementation", "com.fasterxml.jackson.core:jackson-core:2.18.3")
        add("implementation", "com.fasterxml.jackson.core:jackson-annotations:2.18.3")
        add("implementation", "software.bernie.geckolib:geckolib-forge-$minecraftVersion:${node.geckolibVersion}")
    }

    tasks.named<Jar>("jar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        from({
            configurations.getByName("runtimeClasspath")
                .filter { it.name.startsWith("jackson-") }
                .map { zipTree(it) }
        })
    }

    val forgePack = layout.buildDirectory.dir("generated/forge-pack")
    val writeForgePack = tasks.register("writeForgePack") {
        outputs.dir(forgePack)
        doLast {
            val file = forgePack.get().asFile.resolve("pack.mcmeta")
            file.parentFile.mkdirs()
            file.writeText(node.packMeta)
        }
    }

    tasks.named<ProcessResources>("processResources") {
        dependsOn(writeForgePack)
        val props = mapOf(
            "version" to "1.0.0+$minecraftVersion",
            "forge_dep" to node.forgeDep,
            "minecraft" to minecraftVersion,
            "geckolib" to node.geckolibVersion,
        )
        inputs.property("forge_dep", node.forgeDep)
        inputs.property("minecraft", minecraftVersion)
        filesMatching("META-INF/mods.toml") {
            expand(props)
        }
        exclude("fabric.mod.json")
        exclude("pack.mcmeta")
        from(forgePack)
    }

    tasks.withType<JavaExec>().configureEach {
        val shots = providers.gradleProperty("shots").orNull
            ?: "/cursor/stores/bc-1243ce51-36c9-58c7-8fa4-153844455530/media/forge"
        systemProperty("quarkos_cosmetics.shots", shots)
        systemProperty("quarkos_cosmetics.loader", "forge")
        val gallery = providers.gradleProperty("galleryUrl").orNull ?: "http://127.0.0.1:39221"
        systemProperty("cosmetics.galleryUrl", gallery)
        if (providers.gradleProperty("harness").orNull == "true") {
            systemProperty("quarkos_cosmetics.harness", "true")
            args("--width", "960", "--height", "540")
        }
        val display = providers.gradleProperty("display").orNull
        val xauth = providers.gradleProperty("xauth").orNull
        if (!display.isNullOrBlank()) {
            environment("DISPLAY", display)
        }
        if (!xauth.isNullOrBlank()) {
            environment("XAUTHORITY", xauth)
        }
        if (name == "runClient") {
            doFirst {
                val dir = workingDir
                dir.mkdirs()
                dir.resolve("eula.txt").writeText("eula=true\n")
                dir.resolve("client.properties").writeText("galleryUrl=$gallery\n")
                if (providers.gradleProperty("harness").orNull == "true") {
                    val options = dir.resolve("options.txt")
                    val existing = if (options.exists()) options.readText() else ""
                    options.writeText(seedHarnessOptions(existing))
                }
            }
        }
    }
}

fun seedHarnessOptions(existing: String): String {
    val wanted = linkedMapOf(
        "pauseOnLostFocus" to "false",
        "onboardAccessibility" to "false",
        "menuBackgroundBlurriness" to "0",
        "guiScale" to "2",
        "maxFps" to "30",
        "enableVsync" to "false",
        "renderDistance" to "8",
        "simulationDistance" to "6",
        "fov" to "54",
        "mipmapLevels" to "0",
        "entityShadows" to "false",
        "renderClouds" to "\"false\"",
        "particles" to "2",
        "graphicsPreset" to "\"fast\"",
        "inactivityFpsLimit" to "\"minimized\"",
        "fullscreen" to "false",
        "narrator" to "0",
    )
    val lines = existing.lines().toMutableList()
    val seen = mutableSetOf<String>()
    for (i in lines.indices) {
        val line = lines[i]
        val key = line.substringBefore(':')
        val value = wanted[key] ?: continue
        lines[i] = "$key:$value"
        seen += key
    }
    for ((key, value) in wanted) {
        if (key !in seen) {
            lines += "$key:$value"
        }
    }
    return lines.filter { it.isNotBlank() }.joinToString("\n") + "\n"
}

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
    val loaderRange: String,
    val geckolibArtifact: String,
    val geckolibVersion: String,
    val geckolibRange: String,
    val packMeta: String,
)

val minecraftVersion = project.name
val forgeNode = when (minecraftVersion) {
    "1.21.1" -> ForgeNode(
        "1.21.1-52.1.20",
        "52.1.20",
        "[52,)",
        "geckolib-forge-1.21.1",
        "4.9.3",
        "[4.9.3,5)",
        """{"pack":{"description":"Quarkos Cosmetics","pack_format":34}}""" + "\n",
    )
    "1.21.11" -> ForgeNode(
        "1.21.11-61.2.4",
        "61.2.4",
        "[61,)",
        "geckolib-forge-1.21.11",
        "5.4.5",
        "[5.4.5,6)",
        """{"pack":{"description":"Quarkos Cosmetics","pack_format":75,"supported_formats":[75,81],"min_format":75,"max_format":81}}""" + "\n",
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
    val forgeJava = rootProject.layout.projectDirectory.dir("forge/src/main/java")
    val forgeVersionJava = rootProject.layout.projectDirectory.dir("forge/src/$minecraftVersion/java")
    val forgeResources = rootProject.layout.projectDirectory.dir("forge/src/main/resources")
    val forgeVersionResources = rootProject.layout.projectDirectory.dir("forge/src/$minecraftVersion/resources")
    gradle.projectsEvaluated {
        val main = mainSource.get()
        main.java.setSrcDirs(listOf(
            rootProject.file("src/main/java"),
            forgeJava.asFile,
            forgeVersionJava.asFile,
        ))
        main.java.exclude("**/FabricQuarkosCosmetics.java")
        main.java.exclude("**/cosmetics/harness/**")
        if (minecraftVersion == "1.21.1") {
            main.java.exclude("**/render/WingLayer.java")
        }
        main.resources.setSrcDirs(listOf(
            rootProject.file("src/main/resources"),
            forgeResources.asFile,
            forgeVersionResources.asFile,
        ))
        main.resources.exclude("fabric.mod.json")
        main.resources.exclude("quarkos_cosmetics.mixins.json")
        main.resources.exclude("META-INF/mods.toml")
        main.resources.exclude("pack.mcmeta")
        main.output.setResourcesDir(main.java.destinationDirectory)
    }
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
        add("implementation", "software.bernie.geckolib:${node.geckolibArtifact}:${node.geckolibVersion}")
        add("compileOnly", "com.unascribed:ears-api:1.4.5")
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
    val forgeMeta = layout.buildDirectory.dir("generated/forge-meta")
    val writeModsToml = tasks.register("writeForgeModsToml") {
        outputs.dir(forgeMeta)
        doLast {
            val file = forgeMeta.get().asFile.resolve("META-INF/mods.toml")
            file.parentFile.mkdirs()
            val mixins = if (minecraftVersion == "1.21.11") {
                "\n[[mixins]]\nconfig = \"quarkos_cosmetics.forge.mixins.json\"\n"
            } else {
                ""
            }
            file.writeText(
                """
                modLoader = "javafml"
                loaderVersion = "${node.loaderRange}"
                license = "MIT"

                [[mods]]
                modId = "quarkos_cosmetics"
                version = "1.0.0+$minecraftVersion"
                displayName = "Quarkos Cosmetics"
                authors = "Emilio Schwaiger"
                description = '''Free client cosmetics for Forge. Slots are hat, ears, both shoulders, back, and tail, plus an original 3D second skin. Animation is GeckoLib (MIT), loaded as its own mod and not shaded. Ears (MIT) is an optional inhibitor hook. The gallery client talks only to this mod's own server.'''
                displayTest = "IGNORE_ALL_VERSION"

                [[dependencies.quarkos_cosmetics]]
                modId = "forge"
                mandatory = true
                versionRange = "[${node.forgeDep},)"
                ordering = "NONE"
                side = "CLIENT"

                [[dependencies.quarkos_cosmetics]]
                modId = "minecraft"
                mandatory = true
                versionRange = "[$minecraftVersion]"
                ordering = "NONE"
                side = "CLIENT"

                [[dependencies.quarkos_cosmetics]]
                modId = "geckolib"
                mandatory = true
                versionRange = "${node.geckolibRange}"
                ordering = "AFTER"
                side = "CLIENT"

                [[dependencies.quarkos_cosmetics]]
                modId = "ears"
                mandatory = false
                versionRange = "[1.4,)"
                ordering = "AFTER"
                side = "CLIENT"
                $mixins
                """.trimIndent() + "\n"
            )
        }
    }

    tasks.named<ProcessResources>("processResources") {
        dependsOn(writeForgePack, writeModsToml)
        from(forgePack)
        from(forgeMeta)
    }

    tasks.withType<JavaExec>().configureEach {
        val shots = providers.gradleProperty("shots").orNull
            ?: "/cursor/stores/bc-1243ce51-36c9-58c7-8fa4-153844455530/media/forge"
        systemProperty("quarkos_cosmetics.shots", shots)
        systemProperty("quarkos_cosmetics.loader", "forge")
        val gallery = providers.gradleProperty("galleryUrl").orNull ?: "http://127.0.0.1:39221"
        systemProperty("cosmetics.galleryUrl", gallery)
        val watch = providers.gradleProperty("watchFile").orNull
            ?: "/tmp/quarkos-gallery-watch.json"
        systemProperty("cosmetics.watchFile", watch)
        if (providers.gradleProperty("harness").orNull == "true") {
            systemProperty("quarkos_cosmetics.harness", "true")
        }
        if (providers.gradleProperty("skipSettings").orNull == "true") {
            systemProperty("quarkos_cosmetics.skipSettings", "true")
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

    tasks.register<JavaExec>("galleryPeer") {
        group = "application"
        description = "Signs another client into the local gallery and equips a cosmetic."
        classpath = sourceSets.named("main").get().runtimeClasspath
        mainClass.set("dev.quarkos.cosmetics.forge.gallery.GalleryPeer")
        workingDir = rootProject.layout.projectDirectory.asFile
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
        "renderDistance" to "4",
        "simulationDistance" to "5",
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

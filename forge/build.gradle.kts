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
    val packMeta: String,
)

val minecraftVersion = project.name
val forgeNode = when (minecraftVersion) {
    "1.21.1" -> ForgeNode(
        "1.21.1-52.1.20",
        "52.1.20",
        """{"pack":{"description":"Quarkos Cosmetics","pack_format":34}}""" + "\n",
    )
    "1.21.11" -> ForgeNode(
        "1.21.11-61.2.4",
        "61.2.4",
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
    // Both Forge versions sync the shared tree into forge/src. Order the writes
    // and the reads so a combined compile does not race that directory.
    gradle.projectsEvaluated {
        val thisSync = syncBranchSources.get()
        rootProject.subprojects.forEach { other ->
            val otherSync = other.tasks.findByName("syncBranchSources") ?: return@forEach
            if (otherSync == thisSync) {
                return@forEach
            }
            tasks.named("stonecutterPrepare").configure { mustRunAfter(otherSync) }
            if (project.path > other.path) {
                thisSync.mustRunAfter(otherSync)
            }
        }
    }
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
        maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") {
            content { includeGroup("software.bernie.geckolib") }
        }
    }

    dependencies {
        add("implementation", forgeMinecraft.dependency("net.minecraftforge:forge:${node.forgeCoordinate}"))
        add("compileOnly", "com.unascribed:ears-api:1.4.5")
        add("implementation", "com.fasterxml.jackson.core:jackson-databind:2.18.3")
        add("implementation", "com.fasterxml.jackson.core:jackson-core:2.18.3")
        add("implementation", "com.fasterxml.jackson.core:jackson-annotations:2.18.3")
        val geckoVersion = if (minecraftVersion == "1.21.1") "4.9.3" else "5.4.5"
        add("implementation", "software.bernie.geckolib:geckolib-forge-$minecraftVersion:$geckoVersion")
    }

    tasks.named<Jar>("jar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        exclude("META-INF/MANIFEST.MF")
        manifest {
            attributes("MixinConfigs" to "quarkos_cosmetics.mixins.json")
        }
        from({
            configurations.getByName("runtimeClasspath")
                .filter { it.name.startsWith("jackson-") }
                .map { zipTree(it) }
        })
    }

    // Mixin 0.8 on Forge reads MixinConfigs from the jar manifest. mods.toml
    // [[mixins]] is not consulted, and the userdev mod is a classes directory,
    // so the same manifest has to land next to the classes or the client mixins
    // never apply and the mask is never drawn.
    val forgeManifest = layout.buildDirectory.dir("generated/forge-manifest")
    val writeForgeManifest = tasks.register("writeForgeManifest") {
        outputs.dir(forgeManifest)
        doLast {
            val file = forgeManifest.get().asFile.resolve("META-INF/MANIFEST.MF")
            file.parentFile.mkdirs()
            file.writeText(
                "Manifest-Version: 1.0\n" +
                    "MixinConfigs: quarkos_cosmetics.mixins.json\n" +
                    "\n"
            )
        }
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
        dependsOn(writeForgePack, writeForgeManifest)
        val props = mapOf(
            "version" to "1.0.0+$minecraftVersion",
            "forge_dep" to node.forgeDep,
            "minecraft" to minecraftVersion,
            "geckolib" to if (minecraftVersion == "1.21.1") "4.9.3" else "5.4.5",
        )
        inputs.property("forge_dep", node.forgeDep)
        inputs.property("minecraft", minecraftVersion)
        filesMatching("META-INF/mods.toml") {
            expand(props)
        }
        exclude("fabric.mod.json")
        exclude("pack.mcmeta")
        from(forgeManifest)
        // The exclude above also matches a later from(), so the Forge pack
        // metadata has to be copied after the task filter runs.
        doLast {
            copy {
                from(forgePack)
                into(destinationDir)
            }
        }
    }

    tasks.withType<JavaExec>().configureEach {
        val shots = providers.gradleProperty("shots").orNull
            ?: "/cursor/stores/bc-1243ce51-36c9-58c7-8fa4-153844455530/media/fabric"
        systemProperty("quarkos_cosmetics.shots", shots)
        systemProperty("quarkos_cosmetics.loader", "forge")
        systemProperty("cosmetics.galleryUrl", "http://127.0.0.1:39221")
        if (providers.gradleProperty("harness").orNull == "true") {
            systemProperty("quarkos_cosmetics.harness", "true")
            val wide = providers.gradleProperty("wide").orNull == "true"
            args("--width", if (wide) "1920" else "960", "--height", if (wide) "1080" else "540")
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
        if (name == "runClient") {
            doFirst {
                val eula = workingDir.resolve("eula.txt")
                eula.parentFile?.mkdirs()
                eula.writeText("eula=true\n")
            }
        }
    }
}

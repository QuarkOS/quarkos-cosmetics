# Credits

Quark's Cosmetics is MIT, copyright Emilio Schwaiger.

- Jackson Databind, Jackson Core, and Jackson Annotations 2.18.3 are Apache License 2.0. They are included in the Fabric jar and shaded into the Forge jar so gallery JSON matches the gallery service. https://github.com/FasterXML/jackson-databind
- The gallery service uses Javalin 7.2.4 and Jackson 2.18.3, both Apache License 2.0. This mod does not ship Javalin.
- Ears API (`com.unascribed:ears-api`) is compile-only. The optional hide-hook includes the Exa Skye notice in `NOTICE`. Ears is not vendored.
- Mod Menu is an optional Fabric dependency (MIT) and opens this wardrobe. There is no Mod Menu build for Forge.
- GeckoLib 4.9.3 (Minecraft 1.21.1) and 5.4.5 (Minecraft 1.21.11) are MIT and a required mod on both Fabric and Forge. Flap and idle angles are sampled from its baked animation files. The worn meshes stay `cosmetics-model-1` cubes so a published model does not need a second geometry format. https://github.com/bernie-g/geckolib
- Architectury API, Cloth Config, and YetAnotherConfigLib do not publish a Forge artifact that covers both 1.21.1 and 1.21.11. The loader split stays in Stonecutter. The wardrobe is one vanilla screen on both loaders.
- Stonecutter and Fabric Loom are build tools and are not redistributed in the mod jar.

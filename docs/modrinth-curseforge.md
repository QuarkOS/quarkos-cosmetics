# Quark's Cosmetics

Free client cosmetics for Minecraft Java. v1 packs hats and ears, plus an optional gallery for cosmetics you publish yourself.

Local equip works offline. The gallery talks only to this project's server. The mod stores your UUID and does not store your Minecraft access token.

## Required

- Minecraft 1.21.1 or 1.21.11 (Fabric or Forge), or 26.3 (Fabric)
- Fabric Loader and Fabric API, or Forge
- GeckoLib 4.9.3 on 1.21.1, GeckoLib 5.4.5 on 1.21.11, or GeckoLib 5.5.7 on 26.3 (the matching Fabric or Forge build)
- Java 21 (Java 25 on 26.3)

## Optional

- Mod Menu (Fabric only) opens the wardrobe. Forge uses the `K` key and the pause-screen Cosmetics button.
- Ears is not part of this v1 claim and is not bundled.
- A gallery at `http://127.0.0.1:39221`, or another base URL set with `galleryUrl`, `COSMETICS_GALLERY_URL`, or `-Dcosmetics.galleryUrl`. No `/v1` suffix.

## Not required

- Architectury, Cloth Config, and YetAnotherConfigLib are not dependencies. They do not cover Forge on both 1.21.1 and 1.21.11.
- GeckoLib is required. It plays the flap and idle clips. The cubes themselves stay in this mod's model format.
- Tails. If that mod is loaded, this mod leaves its tail slot empty.

Jackson 2.18.3 (Apache 2.0) is bundled for gallery JSON.

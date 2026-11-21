# QuarkOS Cosmetics

Free Minecraft Java client cosmetics by Emilio Schwaiger. The mod id is `quarkos_cosmetics`. It is client-only, MIT licensed, and does not depend on `quarkos_capes`.

The packed wardrobe includes hats, ears, wings, backpacks, shoulder buddies, tails, and body accessories. The chick can perch on your head or either shoulder. The gallery is optional. The client stores a player UUID and never stores a Minecraft access token.

## Run

Java 21 compiles the mod. Fabric Loom in this tree needs a Java 25 Gradle JVM (`JAVA_HOME`).

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew :1.21.11:runClient
./gradlew :1.21.1:runClient
./gradlew :forge:1.21.11:runClient
./gradlew :forge:1.21.1:runClient
./gradlew :1.21.11:test
```

Open the wardrobe with `K`, the pause-screen Cosmetics button, or Mod Menu on Fabric. The default gallery base URL is `http://127.0.0.1:39221` with no `/v1` suffix. Override it with `galleryUrl` in `config/quarkos_cosmetics.properties`, `COSMETICS_GALLERY_URL`, or `-Dcosmetics.galleryUrl`. `client.properties.example` is the same line.

A screenshot harness is off unless you pass `-Pharness=true`. It writes PNGs to `-Pshots=...`.

## Gallery

The game client owns the base URL. Routes start at `/v1`. Sign-in is:

1. `POST /v1/session/challenges` with an empty body.
2. `POST` the access token only to the returned `mojangJoinUrl`.
3. `POST /v1/session` with `{username, serverId}`.

The Bearer token stays in memory. Browse is `GET /v1/cosmetics`. Publish is `POST /v1/cosmetics`. Equip is `PUT /v1/equipment`. Another player's loadout is `GET /v1/equipment/{uuid}`, then `GET /v1/cosmetics/{id}` for each id. Report is `POST /v1/cosmetics/{id}/reports`.

Packed `quarkos:` cosmetics equip locally. Publishing a compatible model creates a gallery id other clients can read. The wardrobe buttons are Sign in, Refresh, the gallery row (equip), Publish packed, and Report.

## Slots

The packed wardrobe reads Blockbench cosmetics and their idle clips. The gallery accepts `cosmetics-model-1`. The [Blockato model inventory](docs/BLOCKATO-MODELS.md) lists the packed pieces and their slots.

To move the chick, select its card in the **Hat**, **Left shoulder**, or **Right shoulder** wardrobe category. The second-skin shell is an original per-pixel extrusion of the 64×64 overlay, culled past the configured distance (default 16 blocks).

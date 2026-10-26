# Quarkos Cosmetics

Free Minecraft Java client cosmetics by Emilio Schwaiger. The mod id is `quarkos_cosmetics`. It is client-only, MIT licensed, and does not depend on `quarkos_capes`.

Hats, shoulders, a back slot (idle wings or bling), a tail, and ears can be equipped locally with no server. An optional gallery URL talks only to this project's own server API. Account proof is Mojang `join`, then the service calls `hasJoined`. The client stores the UUID and never stores the access token.

## Run

Java 21 compiles the mod. Current Fabric Loom needs a Java 25 Gradle JVM (`JAVA_HOME`) on top of that.

```
JAVA_HOME=/path/to/jdk-25 ./gradlew :1.21.11:runClient
```

Versions in this tree: Fabric and Forge for 1.21.1 and 1.21.11. The active Fabric client is `:1.21.11`.

```
./gradlew :1.21.11:runClient
./gradlew :1.21.1:runClient
./gradlew :forge:1.21.11:compileJava
./gradlew :forge:1.21.1:compileJava
```

A screenshot harness is off unless you pass `-Pharness=true`. It writes PNGs to `-Pshots=...`.

## Gallery

Leave `galleryBaseUrl` empty and the client stays local. When it is set, the client calls only that base URL:

- `POST /v1/session` returns a one-time `serverId`
- the client posts that id to `https://sessionserver.mojang.com/session/minecraft/join`
- `POST /v1/session/complete` with `{username, serverId}` returns the UUID to store
- `GET /v1/cosmetics` browses
- `POST /v1/cosmetics` publishes
- `POST /v1/equip` equips
- `POST /v1/report` reports

The access token is a parameter of the join call and is not written to config.

## Models

Player models are Blockbench free models (`.bbmodel`) plus a PNG at most 64×64. Keep the JSON under 48KB, at most 48 cubes, and coordinates between -48 and 80. Do not embed `http` URLs. Groups may set `quarkos_animation` to `flap`, `idle`, or `none`.

## Ears

If Ears is installed, the mod calls `com.unascribed:ears-api` so Ears can hide ears, a tail, or wings that this mod is drawing. Ears is not vendored. See `NOTICE`.

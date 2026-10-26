---
description: Verify the Quarkos Cosmetics Fabric client, gallery contract, and screenshots.
---

# Verify Quarkos Cosmetics

Use this after a client change. Do not rewrite `server/`. Do not publish the mod or the gallery.

## Unit

```bash
JAVA_HOME=/tmp/jdk25 ./gradlew :1.21.11:test
```

`GalleryClientTest` covers two clients that publish, equip, and read each other, including the Mojang join URL from the challenge. Access tokens must not appear in gallery bodies.

## Compile

```bash
JAVA_HOME=/tmp/jdk25 ./gradlew :1.21.1:compileJava :1.21.11:compileJava :forge:1.21.1:compileJava :forge:1.21.11:compileJava
```

## Live gallery

Start `origin/gallery-api` from a worktree, not from this branch. Point `GALLERY_MOJANG_SESSION_ROOT` at a local hasJoined stub when no Minecraft account is available. The production client still posts the access token only to `mojangJoinUrl`.

```bash
JAVA_HOME=/tmp/jdk25 ./gradlew :1.21.11:test -PliveGallery=true
```

That test calls `createChallenge` and `completeSession` (the stub answers hasJoined), then publish, equip, `GET /v1/equipment/{uuid}`, `GET /v1/cosmetics/{id}`, browse, and report.

## Screenshots

```bash
DISPLAY=:1 LIBGL_ALWAYS_SOFTWARE=1 JAVA_HOME=/tmp/jdk25 ./gradlew :1.21.11:runClient -Pharness=true
```

Expect PNGs under the `shots` path, third person, HUD off, the whole player in frame: `kite-wings-front.png`, `kite-wings-back.png`, and the same front/back pair for `lantern-cap`, `fox-ears`, `ribbon-tail`, `ink-wren`, and `pebble-moth`, plus `two-client.png`. Marker pixels: lantern `FF3366`, fox ears `33DD88`, ink wren `3388FF`, pebble moth `FFCC22`, kite wings `CC44FF`, ribbon tail `22E0E0`. Do not treat a settings-only screenshot as the portrait set.

## Launch log

A clean Fabric 1.21.1 and 1.21.11 client log has no `ERROR` from `quarkos_cosmetics` or Mixin.

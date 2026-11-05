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

v1 portraits are head cosmetics only. Expect `<id>-front.png`, `<id>-three-quarter.png`, and `<id>-side.png` for lantern_cap, paper_boat, crescent_circlet, jack_o_lantern_mask, fox_tips, moth_tufts, ribbon_bows, and chick, plus the jack-o'-lantern night set and `two-client.png`. Third person, HUD off, head to feet. Do not treat a settings-only screenshot as the portrait set.

## Launch log

A clean Fabric 1.21.1 and 1.21.11 client log has no `ERROR` from `quarkos_cosmetics` or Mixin.

# Cosmetics gallery

Free public gallery for player-supplied cosmetics. Java 21. MIT, copyright 2026 Emilio Schwaiger.

Publishing, browsing, equipping, and reports are free. There is no paywall and no account besides a Mojang session. The player key is the UUID from `hasJoined`. This process does not call any other cosmetics host and does not import another gallery's catalog.

A later shared backend can serve these same `/v1` routes from one store. Records are keyed by that UUID, and nothing here dials out except Mojang's session server.

The machine-readable spec is [`openapi.yaml`](openapi.yaml). It is also served at `GET /openapi.yaml`.

## Run

From this directory:

```bash
./gradlew run
```

The default listen address is `http://127.0.0.1:39221`. Data is written under `./data`. Copy `gallery.properties.example` to `gallery.properties` to change the bind, port, data directory, Mojang session root, or caps. Environment variables override the file:

| Variable | Default |
| --- | --- |
| `GALLERY_BIND` | `127.0.0.1` |
| `GALLERY_PORT` | `39221` |
| `GALLERY_DATA` | `data` |
| `GALLERY_MOJANG_SESSION_ROOT` | `https://sessionserver.mojang.com` |
| `GALLERY_CONFIG` | `gallery.properties` |
| `GALLERY_MAX_UPLOAD_BYTES` | `262144` |
| `GALLERY_MAX_ELEMENTS` | `64` |
| `GALLERY_MAX_TRIANGLES` | `768` |
| `GALLERY_MAX_TEXTURES` | `4` |
| `GALLERY_MAX_TEXTURE_EDGE` | `128` |
| `GALLERY_MAX_TEXTURE_BYTES` | `65536` |
| `GALLERY_MAX_PUBLIC_PER_PLAYER` | `24` |
| `GALLERY_MAX_RECORDS_PER_PLAYER` | `48` |
| `GALLERY_REPORT_THRESHOLD` | `5` |

`GET /health` returns `{"status":"ok","service":"cosmetics-gallery"}`. `GET /v1/info` returns the live caps and slots. This tree does not deploy the service.

Tests:

```bash
./gradlew test
```

An unstyled console is served at `/`. It calls the API and never asks for an access token.

## Point a local client at it

The game client owns the base URL. This server does not read the client config.

```
galleryUrl=http://127.0.0.1:39221
```

The same value can be supplied as `COSMETICS_GALLERY_URL` or `-Dcosmetics.galleryUrl=http://127.0.0.1:39221`. No path suffix. Routes start at `/v1`. `client.properties.example` is that one line.

Other clients learn what a player is wearing with `GET /v1/equipment/{uuid}`. `{uuid}` is the Mojang id, hyphens optional. Responses always use the hyphenated lowercase form.

## Sign in

The gallery session is not a Minecraft access token. The access token stays in the game and is sent only to Mojang.

1. `POST /v1/session/challenges` with an empty body.

```json
{
  "serverId": "0f6c1b2a9d4e7c8b1a2f3d4e5c6b7a89",
  "expiresAt": "2026-10-26T12:01:30.000Z",
  "mojangJoinUrl": "https://sessionserver.mojang.com/session/minecraft/join"
}
```

The `serverId` is 32 lowercase hex characters, valid for 90 seconds, and good for one successful sign-in. A value this process did not issue is rejected, including a serverId minted for a different service.

2. The already-logged-in client posts that `serverId` to Mojang. This gallery never sees the body.

```http
POST https://sessionserver.mojang.com/session/minecraft/join
Content-Type: application/json

{
  "accessToken": "<Minecraft access token>",
  "selectedProfile": "<profile id without hyphens>",
  "serverId": "<serverId from step 1>"
}
```

Mojang returns 204 on success.

3. `POST /v1/session` with only the username and that serverId.

```json
{ "username": "Ada", "serverId": "0f6c1b2a9d4e7c8b1a2f3d4e5c6b7a89" }
```

This service calls

```text
GET https://sessionserver.mojang.com/session/minecraft/hasJoined?username=Ada&serverId=0f6c1b2a9d4e7c8b1a2f3d4e5c6b7a89
```

and trusts `id` in the JSON response. Texture properties on that payload are ignored. A missing join can be retried until the challenge expires. After a successful check the serverId cannot be used again.

```json
{
  "token": "<gallery session>",
  "tokenType": "Bearer",
  "uuid": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "name": "Ada",
  "expiresAt": "2026-10-27T00:00:00.000Z"
}
```

Send `Authorization: Bearer <token>` on publish, equip, report, and takedown. The token lasts 12 hours. Cache it. Mojang limits how often a client may call join.

`GET /v1/session` returns the UUID and name. `DELETE /v1/session` drops the token.

If a request includes `accessToken`, `access_token`, or `password`, the response is 400 `access_token_rejected` and nothing is written.

## Publish

`POST /v1/cosmetics`

```json
{
  "name": "Straw hat",
  "description": "A wide brim.",
  "slot": "hat",
  "model": {
    "format": "cosmetics-model-1",
    "textures": [
      { "name": "hat", "png": "data:image/png;base64,..." }
    ],
    "elements": [
      {
        "name": "brim",
        "from": [2, 8, 2],
        "to": [14, 10, 14],
        "rotation": [0, 0, 0],
        "origin": [8, 9, 8],
        "faces": {
          "up": { "uv": [0, 0, 12, 12], "texture": 0 },
          "down": { "texture": 0 }
        }
      }
    ]
  }
}
```

Slots are `hat`, `ears`, `shoulder_left`, `shoulder_right`, `back`, and `tail`. `back` is wings or bling. The author stored on the cosmetic is the session UUID.

`201` returns the summary and a `Location` of `/v1/cosmetics/{id}`. The id is 32 hex characters.

Defaults, also returned by `GET /v1/info`:

- Body at most 256 KiB. Larger bodies are `413`.
- At most 64 cubes and 768 triangles. Each listed face is 2 triangles. A cube with no `faces` object counts as 12.
- At most 4 embedded PNGs. Each is 8-bit RGB, indexed, or RGBA, at most 128 pixels on an edge and 64 KiB. `http` and `https` texture URLs are rejected.
- Coordinates stay within -64 and 64. A cube must have extent on one axis.
- At most 24 public cosmetics and 48 stored records per UUID. The record cap only bounds disk use.
- Unknown fields are rejected, including `likes`, `followers`, `price`, and `premium`.

`format` must be `cosmetics-model-1`. Cube fields are the same ones a Blockbench cube uses (`from`, `to`, `faces`, `rotation`, `origin`) so a model can be copied across by hand. Scripts, Molang, and full `.bbmodel` documents are not accepted.

Validation errors are `400`:

```json
{
  "error": "validation_failed",
  "message": "The request is not valid.",
  "violations": [
    { "path": "model.elements", "message": "800 triangles exceeds the cap of 768." }
  ]
}
```

## Browse

`GET /v1/cosmetics?q=straw&slot=hat&limit=20&cursor=0`

Public cosmetics only, newest first. `limit` is 1 to 50 and defaults to 20. `nextCursor` is the next offset, or null.

`GET /v1/cosmetics/{id}` adds `model`. A taken-down id is `404`.

## Equip

`PUT /v1/equipment` stores the signed-in player's selection.

```json
{ "slot": "hat", "cosmeticId": "0123456789abcdef0123456789abcdef" }
```

`cosmeticId: null` clears the slot. The cosmetic must still be public, and its slot must match. Any player may wear any public cosmetic.

`GET /v1/equipment` is the caller's selection. `GET /v1/equipment/{uuid}` is public so other clients can render it. Empty slots are null. A UUID that has never equipped anything returns empty slots, not 404.

## Report and takedown

`POST /v1/cosmetics/{id}/reports`

```json
{ "reason": "stolen", "detail": "Copied from a friend." }
```

Reasons: `spam`, `stolen`, `inappropriate`, `malicious`, `other`. One open report per player. The author gets `403` and should take it down themselves. When distinct reporters reach the threshold (default 5), the cosmetic leaves the gallery. The response then has `"galleryStatus": "taken_down"`.

`POST /v1/cosmetics/{id}/takedown` is the author only.

```json
{ "note": "Replaced it." }
```

The note is optional. A successful takedown removes the model payload and clears that id from stored equipment. Other players see `404`.

## Errors

| Status | `error` | When |
| --- | --- | --- |
| 400 | `validation_failed` | Shape, slot, model, or an unknown field |
| 400 | `unknown_server_id` | serverId was not issued here, or was already used |
| 400 | `expired_server_id` | The 90 second challenge lapsed |
| 400 | `access_token_rejected` | An access token or password was sent here |
| 400 | `bad_uuid` | Equipment path is not a UUID |
| 401 | `unauthorized` | Missing gallery session, or Mojang did not confirm the join |
| 403 | `forbidden` | Reporting your own cosmetic, or taking down someone else's |
| 404 | `not_found` | Unknown route or cosmetic |
| 409 | `conflict` or `limit_reached` | Duplicate report, or a per-player cap |
| 413 | `payload_too_large` | Body over the upload cap |
| 415 | `unsupported_media_type` | Not `application/json` |
| 429 | `mojang_rate_limited` or `too_many_challenges` | Back off and retry |
| 502 | `mojang_unavailable` | The session server could not be used |

Every JSON error body has `error` and `message`.

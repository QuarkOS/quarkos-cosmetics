# Changelog

## 1.0.0

- Packed cosmetics are tapered pixel-art meshes with shaded 16×16 textures. Kite wings use the delivered GeckoLib geometry: folded at rest, and flapping while flying or sprinting.
- Packed wardrobe for hat, ears, left shoulder, right shoulder, back, and tail, with a live player preview.
- Gallery browse, equip, publish, and report against `http://127.0.0.1:39221` (no `/v1` suffix).
- Other players render from `GET /v1/equipment/{uuid}` and `GET /v1/cosmetics/{id}`.
- Category toggles, per-item hide, hide-others, and a 64×64 second-skin shell. Settings persist without an access token.
- Fabric and Forge for 1.21.1 and 1.21.11. The 1.21.11 wardrobe preview clears the scissor stack through the public getter on Forge.

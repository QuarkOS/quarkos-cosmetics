# Changelog

## 1.0.0

- Packed cosmetics are tapered pixel-art meshes with shaded 16×16 textures. Kite wings stay about torso height, folded at rest, and flap from a GeckoLib clip.
- Jack o' lantern mask equips in the hat slot. It is a GeckoLib armor model on the head bone, with a 24-frame candle. The lit texels of the five back-wall faces are ~1px cubes at the eye, nose, and mouth openings. On 1.21.11 the rind and the candle are one mesh drawn into the main color target after the world, so the carvings stay lit on Fabric and Forge. Five flat Euclidean texel rings, starting three block-light levels under the candle and dropping one level each, fade to world light within about three texels and jump with the candle frame. Each eye, nose, and mouth opening has a one-texel flesh side wall running from the front of the rind back to the cavity, brightest against the flame and one block-light level dimmer on the inner strip. Candle faces do not overlap that rim.
- Packed wardrobe for hat, ears, left shoulder, right shoulder, back, and tail, with a live player preview.
- Gallery browse, equip, publish, and report against `http://127.0.0.1:39221` (no `/v1` suffix).
- Other players render from `GET /v1/equipment/{uuid}` and `GET /v1/cosmetics/{id}`.
- Category toggles, per-item hide, hide-others, and a 64×64 second-skin shell. Settings persist without an access token.
- Fabric and Forge for 1.21.1 and 1.21.11. The 1.21.11 wardrobe preview clears the scissor stack through the public getter on Forge.

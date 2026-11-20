# Changelog

## 1.1.0

58 cosmetics instead of 5, a new wardrobe, and Minecraft 26.3 on Fabric.

- 53 new cosmetics: hats, crowns, halos, horns, ears, masks, wings, back pieces and shoulder buddies (for example dragon wings, a jetpack, a witch hat, devil horns, a frog hat, bunny ears and a bee buddy). Body and back slots now equip and render, not just the head.
- Animations: idle, flap and glow clips play, and cosmetics react to you. Jump, hit and crouch clips start on those actions, crouch poses hold, and everything blends back to idle.
- New wardrobe: card grid with a picture of every cosmetic, a count on each tab, a None card and a check on what you're wearing. Scrolls with the mouse wheel, scrollbar or PgUp/PgDn instead of hiding items that don't fit.
- Live 3D preview of your player: drag to rotate or tilt, Reset, and a slow idle spin.
- Real toggle switches for category visible, item shown, hide others and the 3D skin layer, a colour picker for tintable cosmetics, and an 8/16/32/64 m skin-distance control. Lighter gray theme.
- The chick now sits on top of your head instead of hovering above it.
- Fixed the 3D skin layer shell, preview shading, blurry card thumbnails and the jack o' lantern mask's card frame.
- Halos (cyber halo, star tree topper) no longer clip into the head, and the HUD visor goggles line up with the eyes.
- Added Minecraft 26.3 (Fabric, GeckoLib 5.5.7, Java 25). 1.21.1 and 1.21.11 stay on Fabric and Forge.

## 1.0.0

Client-only cosmetics for Minecraft Java 1.21.1 and 1.21.11, on Fabric and Forge. v1 packs five head cosmetics. Emilio approved the jack-o'-lantern night rim and the daylight body shade.

- Chick. Hat perch only.
- Jack-o'-lantern mask. The handoff png, mcmeta, geo, and animation bytes are unchanged. Carved depth, dark-red recesses, a cream mouth core, and an orange shell. A short warm spill flickers on the shell immediately around the eyes and mouth. By day the shell takes the same cardinal shade as the player, so it sits with the hands instead of washing out.
- Scarecrow straw hat. Static head, copied from the Blockbench handoff.
- Candle skull crown. The candle bones play `animation.candle_skull_crown.flicker`, and the glowmask lights the flames.
- Ghost buddy. Drawn at 0.75 scale and shifted `(2.5, -6, -3)` in model space so it floats beside the head, above the shoulder. The bbmodel and textures are the handoff bytes.

Lantern cap, paper boat, crescent circlet, fox tips, moth tufts, and ribbon bows are not in this catalog. Body cosmetics and wings are not equipped or listed. Nothing in this build is uploaded to Modrinth or CurseForge.

Dependencies: GeckoLib 4.9.3 on 1.21.1 and 5.4.5 on 1.21.11. License: MIT.

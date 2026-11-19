# Blockato models

The wardrobe includes the staged Blockbench files and the existing Kite Wings renderer. Each ID in the table uses the `quarkos:` namespace. The five v1 cosmetics and the chick perch behaviour carry over.

`shoulder_left` and `shoulder_right` identify the wearer's sides. Shoulder pieces authored on the torso follow the torso. The Body category holds the scarf, chest core, and waist ring.

The animation column lists the bone loops played during ordinary wear. The original files retain their authored one-shot and held clips. The glow column describes the supplied masks. Status comes from the version-specific `HARD_CHECKS` files, `LIST.md`, and the Blockato finisher queues. OPEN assets are available in the wardrobe per Emilio's integration request.

| Model ID | Slot | Staged source | Idle clips | Glow | Blockato review |
| --- | --- | --- | --- | --- | --- |
| `candy_cane_back_bow` | `back` | v5 | `idle_sway` | None | SHIP candidate. Check the peppermint stripes from behind. |
| `christmas_lights_scarf` | `body` | v5 | `tail_sway` | Static mask | SHIP candidate. Blockato suggested merging the bulb bases. |
| `earmuffs` | `ears` | v5 | `idle_bob` | None | OPEN. Tilt the ear cups outward. |
| `gingerbread_buddy_shoulder` | `shoulder_left` | v7 | `idle_wave` | None | OPEN. Angle the stiff limbs and polish the icing. |
| `ice_crown` | `hat` | v5 | `shimmer_bob` | Static mask | SHIP candidate. Blockato suggested more jagged tips. |
| `reindeer_antlers_headband` | `hat` | v6 | `idle` | None | Lead rated staged v6 ≥4. Emilio approved v9 separately. |
| `santa_hat` | `hat` | v9 | `pompom_bounce` | None | Lead rated v9 ≥4. Polish the flop curve and fur fringe. |
| `snowflake_wings` | `back` | v7 | `flap` | None | SHIP candidate. Sharpen vein and frame contrast. The span is already at the cap. |
| `star_tree_topper_halo` | `hat` | v7 | `slow_spin` | None | OPEN. Saturate the pale gold and brighten the tips. |
| `bat_wings` | `back` | v3b (format 3.2) | `flap` | None | APPROVED v3b. The thin membrane silhouette remains open. |
| `crystal_crown` | `hat` | v4 | None | Animated mask | SHIP candidate. Check the blunt crystal tips. |
| `dragon_wings` | `back` | v4 | `flap` | None | OPEN. Give the scale face more than two tones. |
| `elf_ears` | `ears` | v4 | None | None | OPEN. The inner panel looks muddy and the palette has 10 colours. |
| `fairy_wings` | `back` | v4 | `flutter` | None | OPEN. Thin the wing veins. |
| `floating_spellbook_buddy` | `shoulder_left` | v4 | `hover`, `page_flip` | None | OPEN. Refine the silhouette and pitch, add a face plate, and check the left shoulder placement. |
| `knight_helmet_visor` | `hat` | v4 | `idle` | None | SHIP candidate. Light check requested in the master queue. |
| `phoenix_tail` | `tail` | v4 | `sway` | Animated mask | OPEN. Shorten the 10.2-unit depth and fix the stripes where planes overlap. |
| `unicorn_horn` | `hat` | v4 | None | None | SHIP candidate. The addendum measures the height at 7.71 units above the head. |
| `viking_helmet` | `hat` | v4 | None | None | SHIP candidate. Check the two-tone horns and the band texture. |
| `wizard_hat_stars` | `hat` | v4 | `idle` | Animated mask | OPEN. Use taller, narrower cone segments. The lead approved up to 12 units above the head. |
| `frog_hat` | `hat` | v4 | `blink` | None | Light check. Overlap at 150° arm raise is informational. |
| `cat_ears` | `ears` | v4 | `twitch` | None | OPEN. Cut triangular inner panels and improve the small silhouette and band tones. |
| `fox_tail` | `tail` | v5 | `sway` | None | SHIP. The shaded tip reads beige and the tail exceeds the back depth guideline. |
| `bee_buddy_shoulder` | `shoulder_left` | v4 | `buzz_hover` | None | OPEN. Enlarge the small wings and improve the antenna and stinger tones. |
| `axolotl_hat` | `hat` | v4 | `gill_wiggle` | None | OPEN. The gills read as sticks, and the legs and tail have few tones. |
| `penguin_backpack` | `back` | v5 | None | None | OPEN. The pack now sits higher. The body is boxy and the eye mask reads as stripes. |
| `owl_shoulder_buddy` | `shoulder_right` | v4 | `head_turn`, `blink` | None | OPEN. The staged perch is high for walk and sneak clearance. The catalog lowers it toward the idle shoulder. The head-side wing is static. |
| `bunny_ears` | `ears` | v4 | `idle` | None | OPEN. Add shading on the ear backs and check the tight right root clearance. |
| `duck_float_ring` | `body` | v4 | None | None | OPEN. Round the square ring corners and add neck, beak, and tail tones. |
| `snail_shell_backpack` | `back` | v5 | `eyestalk_wiggle` | None | OPEN. Dome the boxy shell. The eyes face upward and are hard to see from three-quarter view. |
| `cyber_halo` | `hat` | v4 | `idle` | Animated mask | OPEN. Smooth the jagged ring silhouette. |
| `drone_buddy` | `shoulder_left` | v5 | `hover`, `rotor_spin` | Animated mask | OPEN. Give the thin rotor guard visible supports. Check the rotor and glow in game. |
| `energy_core_chest` | `body` | v4 | `idle` | Animated mask | OPEN. The rear plate needs straps. |
| `headphones_led` | `ears` | v5 | `beat` | Animated mask | OPEN. Check slider and cushion overlap and the equalizer glow. |
| `hover_board_back` | `back` | v6 | None | Static mask | OPEN in the queue. Staged v6 raises the board between the shoulders, with depth at the limit. |
| `jetpack` | `back` | v5 | `idle` | Animated mask | OPEN. Check flame brightness, trim, and strap fit on the raised v5 pack. |
| `mech_shoulder_cannon` | `shoulder_left` | v4 | `idle` | Animated mask | OPEN. The rear L-mount looks thin. |
| `robot_antenna` | `hat` | v5 | `idle` | Animated mask | OPEN. Check the rod and band overlap and the idle loop seam. |
| `satellite_dish_hat` | `hat` | v4 | `rotate` | Animated mask | OPEN. The cap reads as a plain slab. |
| `visor_goggles_hud` | `hat` | v4 | `idle` | Animated mask | OPEN. Strengthen the hologram and check the deep silhouette. |
| `balloon_buddy` | `shoulder_left` | v5 | `bob`, `sway`, `blink`, `look` | None | OPEN. Blockato wanted the balloon higher and farther to the side within the clipping limits. |
| `boba_tea_backpack` | `back` | v7 | None | None | SHIP candidate v7. The pink harness and longer straw improve the front silhouette. |
| `chef_hat` | `hat` | v5 | None | None | OPEN. The silhouette, rounded shape, and texture each scored 3. |
| `cupcake_hat` | `hat` | v6 | `idle` | None | OPEN. The silhouette scored 3. |
| `donut_float_halo` | `hat` | v5 | `spin` | None | OPEN. The silhouette and curves scored 3. |
| `fish_bowl_helmet` | `hat` | v5 | `swim` | None | SHIP candidate. Check how clearly the fish reads through the glass. |
| `party_hat_confetti` | `hat` | v6 | `idle` | None | OPEN. Reshape the cone, slim the band, and check the tilt and confetti planes. |
| `pizza_slice_hat` | `hat` | v5 | None | None | OPEN. The pizza wedge disappears edge-on. |
| `propeller_beanie` | `hat` | v7 | `spin` | None | SHIP candidate v7. |
| `traffic_cone_hat` | `hat` | v5 | None | None | SHIP candidate. |
| `devil_horns_headband` | `hat` | v2 | `idle_twitch` | None | OPEN. Staged v2 was superseded by a v3 retexture in the notes. The v3 band decals and horn taper remain open. |
| `witch_hat` | `hat` | v2 | `idle_sway` | None | v2 texture pass. Blockato recorded zero overlap in the required poses. |
| `kite_wings` | `back` | Antigravity geo | `idle` | None | Restored from the Antigravity assets. No Blockato finisher entry. |

## Source and placement notes

- The manifest has 53 eligible entries. It lists Bat Wings in addition to the 49 numbered batch models, the two Halloween models, and Kite Wings.
- Emilio approved Reindeer Antlers v9. This integration uses the staged v6 headband and its texture.
- The staged Christmas Lights Scarf glow mask contains a single frame. The attached metadata describes its timing, so that frame repeats. The Star Tree Topper Halo has a preview glow note and ships here with its staged unlit texture.
- The candy cane bow, snowflake wings, gingerbread buddy, owl, and scarf use catalog placement offsets in authored model pixels. The bow and wings sit lower on the back to clear the head. The scarf band sits over the jacket with the bulbs below the head.
- Santa's brim starts one pixel below the base head top. The brim uses that authored overlap to seat the hat. The star topper starts 1.5 pixels above the head and adds up to 0.5 pixels of bob. Blockato's proof uses that hover.
- The pizza slice is a horizontal plane. Its tip points toward the drip over the brow. The slice appears thin at eye level. The straight front view shows both earmuff cups.
- The Blockbench renderer binds animated PNGs as full strips under private texture IDs. It uses the same frame metadata for alpha checks and UV sampling. GeckoLib exposes frame-sized textures under the original resource IDs. [GeckoLib animated texture implementation](https://github.com/bernie-g/geckolib/blob/main/common/src/main/java/com/geckolib/renderer/texture/GeckoLibAnimatedTexture.java)

## Excluded

`snowman_head_hat` v5. Emilio dropped it from the set on 2026-11-10.

## Windows client captures

The Windows test script accepts `-Only blockato` to capture the added models in one studio world. It writes a three-quarter `model-<id>.png` for each addition, a straight rear `model-<id>-back.png` for Back and Tail, and a straight front `model-<id>-front.png` for Ears. Back and Tail three-quarter shots also face the rear. Each shot has a matching no-cosmetic image under `model-baselines/`. The script also writes `wardrobe-<slot>-page-<n>.png` for each category page.

The harness reports `MODEL id=<id> loaded=true cubes=<n> anim=<clip|none> slot=<slot>` for every catalog entry. Multiple active clips appear joined with `+`. A failed load reports `loaded=false` and ends the harness with `HARNESS_FAILED model_load`.

Each equipped shot reports `MODEL_VISIBLE id=<id> changedPx=<n> bboxPx=<w>x<h> view=<front|back>`. The comparison measures the center two thirds of the frame. It counts pixels with an RGB channel difference of at least 8. At 1920×1080 the minimum is 69 pixels. The harness saves all model shots and reports `HARNESS_FAILED model_invisible` if any fall below the minimum. Look for `MODEL_SHOT_DONE`, `WARDROBE_PAGE_DONE`, and `HARNESS_DONE` on a passing run.

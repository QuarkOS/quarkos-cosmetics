# Equip and wear cosmetics

Choose a built-in cosmetic and see it on the player in third person.

## Sub-features

- `catalog` loads every shipped cosmetic.
- `wear` equips one cosmetic at a time in its slot.
- `fit` checks the worn region in five views and a close-up.

## How to get to it (user POV)

Open **Cosmetics** from the pause menu. Select a Wardrobe category, choose a card, and return to the world. Switch to third person.

## Driving it with the release runner

Run `.\scripts\verify-models.ps1 -Command Launch -Version 26.3` on Quark. Check that `done.json` exits with code 0. Compare each ID's five views and close-up with its unequipped view in `evidence/report.json`.

## Gotchas

- Check the side view for a small contact point or gap.
- For a version comparison, keep the same Minecraft version and player options in both runs.

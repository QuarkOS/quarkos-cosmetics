# Wardrobe preview

The Wardrobe shows the available cosmetics by slot and draws the worn item on the preview player.

## Sub-features

- `categories` opens each slot's cards.
- `pages` moves through the available cards.
- `preview` shows the selected equipment as the player turns.

## How to get to it (user POV)

Open **Cosmetics** from the pause menu. Choose a Wardrobe category. Scroll the cards and drag the preview player.

## Driving it with the release runner

Run `.\scripts\verify-models.ps1 -Command Launch -Version 26.3` on Quark. Open `evidence/wardrobe` after capture. Check each category's first page for the equipped model and inspect the later pages for missing cards.

## Gotchas

- A filtered run captures the requested models in-world. The full run includes Wardrobe pages.
- The preview uses the Wardrobe renderer. Compare it with the in-world view when only one looks wrong.

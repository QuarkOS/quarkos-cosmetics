# Chick head perch

Equip the chick on the player's head and check that its feet rest on the hat layer.

## Sub-features

- `head-seat` records the gap and relative width.
- `sit` samples the seated animation.

## How to get to it (user POV)

Open the Hat category and choose **Chick**. Return to the world and turn the third-person camera.

## Driving it with the release runner

Run `.\scripts\verify-models.ps1 -Command Launch -Only chick` on Quark. Read `CHICK_PERCH` in the report. Confirm a zero gap within the tolerance and a chick width near 0.67 head widths. Inspect the close-up for both feet.

## Gotchas

- The head measurement uses the visible hat layer and no helmet. A shoulder capture measures a different seat.

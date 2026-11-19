# Cosmetic motion

A worn cosmetic may animate while idle or when the player performs an action.

## Sub-features

- `idle` samples each authored loop.
- `texture` records animated texture strips.
- `action` samples authored walk, flap, jump, hit, and sneak phases where the model provides them.

## How to get to it (user POV)

Equip the cosmetic and watch it in third person. For an action clip, move, jump, take a hit, or crouch as specified by its name.

## Driving it with the release runner

Run `.\scripts\verify-models.ps1 -Command Launch -Only dragon_wings,chick` on Quark. Open each model's animation strip. Check the time series in `evidence/report.json` for movement and the return to rest.

## Gotchas

- The driver fixes the camera and samples clip time. Chick and kite wing samples use their clip holds. Check an actual player action separately when changing how an event is selected.
- Multiple idle clips can have different periods. The report marks a loop-seam comparison only when those periods align.
- A held pose has a deliberate end pose. An authored one-shot should return to its rest pose.

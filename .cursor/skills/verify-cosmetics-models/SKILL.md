---
name: verify-cosmetics-models
description: Verify the Quarkos Cosmetics Fabric client on Quark. Equip every shipped cosmetic and check its in-world fit, looks, motion, and Wardrobe preview before a release.
disable-model-invocation: true
---

# Verify Cosmetics models

Use Quark for Gradle and Minecraft. Use a release run for the complete catalog and a filtered run to investigate a model. Read the [feature map](features/README.md) for user paths and proof.

## Launch

Commit the revision on `codex/cos-verify` and sync it to Quark with `/workspace/ui-pass/pass2/cosv/qsync.sh`. On Quark, run the following from `D:\dev\cosmetics`. The command returns a run directory and a task name.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-models.ps1 -Command Launch -Version 26.3 -Label BEFORE
```

Pass the same command to `/workspace/ui-pass/pass2/cosv/qps.sh` from the box. The runner starts a one-shot Interactive/Limited scheduled task via `conhost --headless`. The task creates a separate Win32 desktop and starts Gradle and Minecraft in a private job. The run directory contains its own game profile, options, and flat world. Master volume is zero and `pauseOnLostFocus` is false.

Add `-BuildChecks` to run JUnit and the Fabric and Forge compile matrix after capture. Use `-BuildOnly` for the same checks without starting Minecraft.

## Doctor

On Quark, run `.\scripts\verify-models.ps1 -Command Doctor`. Check the branch and commit against the revision you synced. Doctor checks the portable JDK, Gradle cache, Python with Pillow and NumPy, signed-in session, and lock age. It reads the current state.

After launch, pass the returned path as `-RunDir` to Doctor. Expect the scheduled task to be running and `ready` to become true after the client logs `studio ready`. Use `-Command Status -RunDir $run` for short SSH polls and `-Command Wait -RunDir $run` in a local Quark terminal when you need the final exit code. A failed client writes a nonzero code in `done.json`.

## Drive

A full release run takes its inventory from `cosmetics/catalog.json` and requires the client to load the same set. At the initial release revision the catalog contains 53 Blockato entries and five v1 entries. The Wardrobe uses the same catalog. The other geometry resources in the tree do not add equipped built-ins.

The client creates a local world, clears the slots, and equips each model through the same settings used by the Wardrobe. It renders the actual player with that equipment. The client driver controls the camera and animation clock. It captures the no-cosmetic player and the empty studio at each camera and pose. A full run also opens each Wardrobe category and saves every page with a preview.

To investigate an item, add `-Only chick,visor_goggles_hud`. Keep the catalog-wide load check. Use the full run for release evidence.

## Evidence

Read `evidence/REPORT.md` and `evidence/report.json` in the run directory. The report links the five views, the worn-region crop, sampled animation frames, and the strip for each animated model. It records changed pixels, empty pixels at a seat, model-space fit, and the value and threshold for each finding. The offline mannequin uses quads exported by the game renderer. It helps measure fit and cannot stand in for the Minecraft frame.

Inspect every model in the all-model contact sheets. Open the original downscaled views and strips when a tile hides the relevant feature. Check the flagged sheets against the reported reasons. Record the IDs you inspected in a review JSON file with a `seen` array of full catalog IDs. An optional `notes` object maps IDs to an object with a `note` and a `dismiss` array of check names. Apply the review to the exported evidence directory.

```bash
python3 -B scripts/verify-models/report.py /workspace/ui-pass/pass2/cos-verify/after --review /tmp/cosmetics-review.json
```

Keep the 1920 by 1080 frames on Quark in `raw`. Fetch the `evidence` directory with `/workspace/ui-pass/pass2/cosv/qfetch.sh`. Keep the combined box evidence under 400 MB.

## Cleanup

The task releases its own lock, closes its process job, and unregisters itself. The job contains only the processes that run started. The next run reserves a fresh dated directory and retains prior evidence. A timeout closes the job before releasing the lock. Read `logs/run.log` and `done.json` to check teardown.

The test lock is `D:\dev\mc-test.lock`. A recent lock delays capture for up to 90 minutes by default. The task refreshes its own marker during capture. If a lock is at least 30 minutes old and can be opened for reclamation, the task moves it into the new run directory.

## Helpers

- `scripts/verify-models.ps1` launches, checks, and waits for runs. `scripts/verify-models/HiddenDesktop.ps1` owns the private desktop and process job.
- `python -B scripts/verify-models/plan.py D:\dev\cos-verify-out\plan.json` writes the capture plan from the catalog, model animations, and texture strips.
- `python -B scripts/verify-models/report.py $run` rebuilds evidence from a completed run on Quark.
- `python3 -B scripts/verify-models/compare.py before after beforeafter visor_goggles_hud` creates the side-by-side views from exported results. Run it from the repo with the actual evidence paths.
- [Thresholds](thresholds.md) describes `scripts/verify-models/thresholds.json` and the measurements in the report.

Use `/maintain-verification-skill` when the client or the feature map changes.

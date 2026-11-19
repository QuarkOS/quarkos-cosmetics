# Model checks

The values in `scripts/verify-models/thresholds.json` come from the Blockato design bible and the release fit rules. Model units are sixteenths of a block. The vanilla head is 8 units wide and the helmet is 10. The wider brim limit applies to straw, wizard, and witch hats.

| Check | Measurement | Classification |
| --- | --- | --- |
| Visibility | Maximum changed pixels across the five live views against the matching baseline. The pixel limit scales with resolution. | Mod-side |
| Attachment | Minimum model-space distance to the slot's seat and empty pixels between the visible model and baseline silhouette. Intentional floaters have separate clearance rules. | Mod-side |
| Placement | Model-space center by slot, halo height, wing root height, and visor center against the eye line. | Mod-side |
| Face | Front-view eye and mouth coverage for headwear that should leave the face clear. Rendered surfaces also test penetration into the head and visor clearance. | Mod-side |
| Pair balance | Ratio of the smaller to the larger side in the front or rear view. | Art |
| Proportion | Rendered width, height, depth, or span for the applicable item type. | Art |
| Curves | Unrotated horn segments, horizontal levels in tapered hats, and rotated segments in Santa's flop. | Art |
| Motion | Adjacent-frame pixel changes and peak change per sampled tick. Loop seams compare the last tick with the wrapped frame when the other clocks also repeat. | Missing motion is mod-side. Jumps and authored seams are art. |
| Chick perch | The renderer measures the gap and the chick width against the head. | Mod-side |

Model-space limits describe the posed geometry at the first capture tick. The live seat measurement uses a diagonal pixel neighborhood. A visible overlap can hide a depth gap, so check the side view and model-space distance before changing a mount. A paired outline can look asymmetric because it has an authored accessory. Inspect the image before assigning the finding to Modelato.

The legacy v1 pieces keep their existing fit and style. They still use the visibility, motion, and chick perch checks.

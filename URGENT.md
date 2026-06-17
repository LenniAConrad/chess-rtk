# RESOLVED: OTIS tactical-incidence pawn channel was backward, now fixed

**Date:** 2026-06-02
**Severity:** High — a network input channel was geometrically inverted in both the
CPU and the native GPU encoders.

## What was wrong

The OTIS `pawn_attack_forward_oriented` relation channel (channel 10 of the
tactical-incidence tensor `A(x)`) pointed **backward**: pawns "attacked" toward their
own camp instead of forward toward the enemy. A white pawn on e2 was given attack
edges to d1/f1 instead of d3/f3.

chess-rtk indexes squares with `0 = a8`, so `from >>> 3` (the internal rank) runs
`0 = 8th rank` down to `7 = 1st rank`; a pawn advancing forward therefore **decreases**
the internal rank for White. The encoder used the wrong sign
(`isWhite ? 1 : -1`), aiming the attacks backward. This fed both channel 10 **and** the
pawn's contribution to the attack / king-zone channels.

## Where (all kept byte-identical for CPU/GPU parity)

- `src/chess/nn/otis/Model.java` — `addPawnRelations` (CPU encoder). **Fixed**
  (`isWhite ? -1 : 1`).
- `native/common/otis_gpu_impl.inl` — `add_pawn_relations` (native host-side encoder
  shared by CUDA/ROCm/oneAPI). **Fixed**; `native/cuda/build/libotis_cuda.so` **rebuilt**.

`OtisBackendRegressionTest` confirms CPU == GPU with the corrected direction;
`CLICommandRegressionTest` asserts the forward orientation (e2 → d3/f3). `fen relations`
renders the corrected encoder directly — the earlier render-only correction was removed.

## Model impact

The bundled `models/otis_policy_wdl_random.bin` is a random placeholder, so its outputs
were never meaningful. **But** any *real* OTIS weights converted from a
chess-nn-playground checkpoint trained before this fix were trained on the backward
pawn channel and must be **re-exported from a retrained checkpoint**.

## Resolution

Checked on 2026-06-16: the only default OTIS weight path in this repository is
`models/otis_policy_wdl_random.bin` (`chess.nn.otis.Model.DEFAULT_WEIGHTS`), and
the local `models/` directory contains no production OTIS `.bin` file. The default
file is a randomized placeholder rather than a trained pre-fix model, so no
repository-shipped OTIS weight needs re-exporting.

## Origin

This builder mirrors `chess-nn-playground`'s
`oriented_tactical_sheaf.py` (`TacticalIncidenceBuilder` / `_make_geometry_masks`),
which had the **same** bug. It is fixed there too — see that repo's `URGENT.md`. Note
the two repos use **mirrored** square conventions (this repo `0 = a8`, the playground
`0 = a1`), so the fix sign differs between them while the geometric result (forward) is
the same.

## Action items

- [x] No production OTIS `.bin` weights are shipped or referenced by defaults; no repository re-export is required.
- [x] Live follow-up closed; this file is retained only as a historical note for local/private pre-fix weights.

# Engine Strength Tuning — What Worked, What Didn't

A running log of attempts to make the in-house `AlphaBeta` engine stronger, with the measured outcome of each. The point is to **not relearn the same lessons**: which techniques paid off, which didn't, and — more importantly — the *methodology and gotchas* that decide whether a measurement can be trusted at all.

The one rule that produced everything below: **measure with self-play, never assert.** Several textbook techniques that "should" help measured neutral or negative here; several that looked neutral at first were just under-sampled. Ship a change only when a gauntlet says it's non-negative.

## How to measure (the harnesses)

- **`src/testing/SelfPlayGauntlet.java`** — in-process A/B. Plays candidate vs baseline from a set of openings, **each opening from both colors** (so identical engines score exactly 50.0% — verified), at an **equal fixed budget** (`--nodes N` or `--movetime MS`). Prints W-D-L + a point Elo. Flags: `--a/--b CSV|all|none` (feature sets), `--threadsA/--threadsB`, `--workers N` (parallel opening-pair workers), `--eval classical|nnue`, `--openings N` (seeded reproducible generation), `--seed S`.
- **`src/testing/NnueBench.java`** — NNUE correctness + speed. Prints a **golden** centipawn vector (must be byte-identical across builds → no eval change), a **parity** walk (incremental search-state eval == full eval at every make/undo node), a **concurrent parity** (8 threads, proves thread-safety), and an **nps** benchmark.
- **`AlphaBeta.Feature`** enum + the 3-arg constructor let each technique be toggled independently, so the gauntlet can A/B one change in isolation, in one process.

Build and run from a **private output dir** (see the gotcha about `out/` below), e.g.:

```bash
OUT=/tmp/crtk-ab && rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 17 -d "$OUT" $(find src -name '*.java')
java -cp "$OUT" testing.SelfPlayGauntlet --a SEE_PRUNING,SEARCH_REPETITION --b none \
  --eval classical --nodes 5000 --openings 150 --workers 8
```

## What worked (shipped)

| Technique | Measured gain | Notes |
| --- | --- | --- |
| **SEE pruning + capture ordering** | **+73 Elo** (300 games, classical, fixed nodes) | Wire the existing `chess.eval.See` into the search: prune SEE-losing captures in quiescence; order losing captures last. Only "capturing up" (victim < attacker) can be SEE-negative, so guard the SEE call on that. |
| **In-search repetition detection** | `{SEE,REP}` vs old engine = **+106** fixed-nodes, **+61** fixed-time, **+53** NNUE | The engine previously could not see repetitions/perpetuals during search. Scan a per-ply `signatureCore` path (step −2, bounded by the halfmove clock); return 0 on a repeat. Null move stores a sentinel so it can't match. |
| **NNUE incremental accumulator for BIG nets** | **+34% nps**, output-preserving | The production 89MB net is a BIG (SFNNv13 + threats) net, for which `newSearchState` returned `null` — so every leaf ran a *full* feature-transformer rebuild (the incremental accumulator was dead code). `BigSearchState` keeps HalfKA PSQ incremental and rebuilds only threats per node. Verified bit-identical by a 7,759-node parity walk (0 mismatches). |
| **Diversified Lazy SMP** | **+67** (classical), **+66** (NNUE), **~+89** with the lockless TT | Naive SMP was *negative* (see below) — the fix is helper-thread **depth-skip diversification** (the classic `SKIP_SIZE`/`SKIP_PHASE` scheme) so threads cover different depths and fill the shared table diversely. Measured at fixed time. Enabled **only at the Max strength setting**. |
| **LMR table + check extensions** | **+32 Elo** at deep search (600 games) | Log-log LMR reductions + one-ply check extensions. *Neutral/negative when search is shallow* (see below) — enabled **only at the Max setting**, where NNUE + SMP make the search deep. |
| **Internal iterative reduction (IIR)** | **+14 Elo** (300 games, classical, 20k fixed nodes) | When a deep node has no transposition move to order first, search it one ply shallower to seed the TT cheaply instead of spending full depth with poor ordering. A shallow 5k-node screen was flat, so this is a depth-scaling gain. |
| **Root, fallback, and horizon mate proof shortcuts** | tactical correctness | Alpha-beta now checks root mate-in-one directly, runs the bounded mate prover on forcing roots before spending heuristic search nodes, detects mate-in-one at the low-budget root fallback, detects mate-in-one at the quiescence horizon, and searches first-ply quiet checks in quiescence. Short proven mates return exact `#N` scores instead of static centipawns. |
| **Mate-score TT normalization + mate-distance pruning** | tactical correctness / pruning | Mate scores are stored in the transposition table relative to the stored position and converted back at probe ply, so reused entries preserve the correct mate distance. With that normalization in place, internal mate scores can also be stored and reused safely instead of being dropped. Main and quiescence search clamp impossible mate-distance windows before searching. Stockfish-style idea, implemented in the local alpha-beta frame. |
| **Tear-tolerant, depth-preserving transposition table** | correctness / TT quality | Pack each entry's depth/score/flag/move into one `long`; store the signature XOR-ed with it; validate via `(key ^ data) == signature`. A reader catching a concurrent (SMP) store mid-write fails the check and misses instead of returning a corrupt score. Same-generation replacement now also preserves deeper entries from shallower overwrites, including same-position rewrites. |

The shipped alpha-beta default feature set is `{SEE_PRUNING, SEARCH_REPETITION, LMR_TABLE, IMPROVING, CONT_HISTORY, CHECK_EXTENSION, IIR}`. CLI searches stay single-threaded by default for reproducibility, but `engine builtin --threads N` opts into Lazy SMP; `engine builtin --max-strength` selects the time-bound alpha-beta profile and automatic Lazy SMP thread count used by the Workbench Max setting. Features that measured neutral or negative remain off by default behind `AlphaBeta.Feature`.

## What did NOT work (rejected — kept as off-by-default toggles)

| Technique | Measured | Why it's off |
| --- | --- | --- |
| **Naive Lazy SMP** (all threads run the identical search) | **−29 Elo** | Threads duplicated one search; the only benefit was TT cross-fill, swamped by contention. Diversification (above) was the fix. |
| **LMR table at shallow search** | +2 (neutral) | Helps only once search is deep — see the depth-scaling lesson. |
| **Check extensions at shallow search** | **−6** | Spends node breadth on forcing lines when there's no depth to exploit. |
| **History malus / gravity** | +6 over 600 games (neutral); rechecked at +3 over 300 games; current default stack recheck **−24** over 100 games | Bonus-to-cutoff + malus-to-tried-quiets with decay. No measurable effect at this engine's depths, and the current default stack did not benefit. |
| **SEE-based bad-capture LMR** | +7 (neutral) | Reducing SEE-losing captures (instead of searching them full-depth). Within noise. |
| **Capture history** | +47 over 60 games, then −2 on a 200-game confirmation and −3 on a 100-game tie-breaker; positive-only variant −23 over 60 games; 50k-node recheck **−3** over 120 games | Stockfish-inspired capture ordering table keyed by moving piece, target square, and captured piece type. Kept behind `AlphaBeta.Feature.CAPTURE_HISTORY`; independent confirmations did not support enabling it by default. |
| **Main-search quiet-check ordering** | −13 at 220 games (stopped early) | A bonus for quiet checking moves looked plausible after quiet-check quiescence, but the longer run turned negative before the full sample. Kept off. |
| **ProbCut tactical pre-search** | +6 over 60 games, +2 over 200 games, then −10 on an independent 100-game seed; combined 300 games ≈ −2; 50k-node recheck **−12** over 120 games | Stockfish-inspired beta-plus-margin tactical pre-search, independently implemented behind `AlphaBeta.Feature.PROBCUT`. The safe constants were neutral; a more aggressive depth-4/margin-180 variant measured **−47** over 60 games. Keep off until deeper/faster settings justify remeasurement. |
| **Root score ordering** | +20 over 120 games, then **−41** on an independent 120-game confirmation | Previous-depth root scores can order non-PV root moves on the next depth (`AlphaBeta.Feature.ROOT_SCORE_ORDERING`), but the confirmation seed rejected enabling it by default. |
| **Correction history recheck** | **−53** over 60 games | Static-eval correction history is still available as `CORRECTION_HISTORY`, but the classical fixed-node recheck with the current default stack was clearly negative. |
| **Singular extensions recheck** | −6 over 60 games | The TT-move singular-extension verifier was neutral-negative and slower at this budget, so it remains off. |
| **Razoring recheck** | **−29** over 60 games | Shallow fail-low razoring did not pair well with the current default stack at 20k fixed nodes. |

These remain in the code behind `AlphaBeta.Feature` so they can be re-measured (e.g. after a future depth increase) without reimplementing them.

## The lessons that will save the most time

1. **Sample size dominates.** A 48-game gauntlet has roughly **±100 Elo** of noise — enough to flip a true +73 to "+22" or a true 0 to "−29." Several techniques read "neutral" or "negative" at 48 games and then settled at a different value by 300. **Use ≥300 games before believing a ship/no-ship call**, and confirm a marginal result with a second seed.

2. **Depth-scaling techniques are invisible at shallow depth.** LMR, check extensions, history, singular extensions, etc. pay off *more as search deepens*. This engine searches shallowly (NNUE ~12–28k nps; classical reaches ~depth 6–10 at a few-thousand-node budget), so they read neutral/negative on a shallow gauntlet. They flipped clearly positive at 20–30k nodes. **Don't reject a depth-scaling heuristic on a shallow measurement — re-test it deep**, and gate it to the deep (Max) regime if it hurts when shallow.

3. **Lazy SMP must diversify.** N identical deterministic threads ≈ one search + overhead = *negative*. Helpers need a depth-skip (or move-order) scheme so they explore different parts of the tree. **Measure SMP at fixed *time*, not fixed nodes** (more threads = more nodes per unit time is the entire point).

4. **The NNUE "incremental accumulator" can be dead code.** For BIG nets it returned `null`, so the AB path fell back to a full forward pass per leaf — the real reason NNUE was slow. **Before optimizing NNUE, confirm which path actually runs.** An output-preserving speedup (verified bit-identical via the parity walk) needs *no* Elo measurement — more nps at identical eval is a pure win (deeper search in the same time).

5. **Elo is not additive.** Per-change gains were each measured against the *previous* config under different conditions; you can't sum them. For a real total, run the **full new config vs the original engine head-to-head** at fixed time.

## Environment / tooling gotchas

- **The IDE clobbers `out/`.** The VS Code `redhat.java` (Eclipse JDT) language server auto-builds the project on every file edit and intermittently rewrites/empties `out/`, causing flaky `ClassNotFoundException` mid-run. **Compile and run from a private dir** (`/tmp/crtk-…`) the IDE doesn't manage. (The running workbench GUI from `crtk.jar` can also hold the tree.)
- **Use game-level workers for fixed-node gauntlets.** `SelfPlayGauntlet --workers N` runs independent opening pairs concurrently and collects them in opening order, so fixed-node results stay deterministic while using more cores. Keep `--threadsA 1 --threadsB 1` for these screens unless you are explicitly measuring Lazy SMP.
- **Don't parallelize fixed-time gauntlets casually.** Multiple workers contend for cores and skew wall-clock budgets. For Lazy SMP strength tests, prefer `--movetime` with `--threadsA/--threadsB` and `--workers 1`.
- **Build with `javac --release 17`.** Bare JDK-21 emits false errors; the project targets 17. The Java Vector API (`jdk.incubator.vector`) is **not** available under the stock `--release 17` launch, so SIMD NNUE is out without a build/launch change.
- **Allocation cleanups are not Elo claims.** Reusing a scratch `Position.State` for checking-move probes and per-ply move-order score rows removes hot-path allocation, but fixed-node tests cannot show speed gains directly. Verify output stability (for example same-engine fixed-node self-play at 50.0%) and use longer fixed-time gauntlets before calling it Elo.
- **Determinism is a feature here.** Anything non-deterministic (Lazy SMP, sampling) is gated so the CLI, tests, and lower Play strengths stay reproducible.

## Open levers (not yet attempted — risk noted)

- **Incremental NNUE *threats*** — threats ray-walk sliders and depend non-locally on occupancy, so a per-move delta risks silently changing eval. Only pursue behind the bit-exact parity harness; treat any divergence as a bug, not a tradeoff. Estimated *modest* (the threat rebuild is ~40% of the per-node cost).
- **Broaden SMP / deep-heuristics below the Max tier** — a calibration tradeoff (it strengthens settings beyond their nominal Elo); make it an explicit Play "use all cores" option rather than silently shifting the slider's meaning.
- **Re-test the shelved heuristics now that search is deeper** (faster NNUE + SMP) — the depth-scaling lesson predicts some may now help.

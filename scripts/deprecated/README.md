# Deprecated Scripts

This directory holds retired helper scripts kept **for historical reference only**. They are no longer part of the supported ChessRTK automation surface, are not tested, and may break against the current `crtk.jar`. Most of them predate ChessRTK's move to a single shared Java 17 chess core and the deterministic noun-verb CLI, so their work now lives inside `crtk` commands you should use instead. Active, supported scripts live one level up in `scripts/`.

> Do not build new workflows on anything in this folder. Treat these files as a changelog of how earlier pipelines worked, then reach for the documented CLI equivalents.

## Why these are deprecated

These scripts mostly wrapped the old `crtk.jar <verb>` flat command style (for example `crtk.jar render ...` or `crtk.jar analyze ...`) and bolted external tooling around it. That logic has since been folded into the shared chess core and exposed through stable, deterministic commands such as `fen render`, `fen tags`, `fen text`, `puzzle mine`, `puzzle tags`, and `puzzle text`. The native, in-process paths are reproducible and need no Python glue, so the standalone scripts were retired.

See [Command Reference](command-reference.md) and the [Command Cheatsheet](command-cheatsheet.md) for the current surface.

## What replaced each script

| Retired script | What it did | Use instead |
| --- | --- | --- |
| `analysis_pipeline.py` | Ran external-engine analysis and stitched tags into deterministic narrative text. | `fen tags` for tagging, `fen text` for T5 summaries, `engine analyze` for engine output. |
| `azure_tag_text.py` | Called a hosted Azure model to turn tag sets into prose. | `fen text` and `puzzle text` (in-process T5 natural-language summaries; no external service). |
| `puzzle_azure.py` | Hosted-model captioning for mined puzzle lines. | `puzzle text` (T5 over puzzle PVs). |
| `puzzle_tags.py` | Wrapped `crtk.jar analyze` to derive per-move puzzle tags. | `puzzle tags` for puzzle PV tagging; `puzzle mine` for the full mining pipeline. |
| `build_t5_dataset.py` | Built a T5 training corpus from FEN JSONL via the analysis pipeline. | `record export training-jsonl` and `record dataset` for ML dataset export. |
| `generate_github_banner.sh` | Rendered fixed positions with `crtk.jar render` and composited a banner. | `fen render` for the position images; banner assembly is a one-off, not a product feature. |
| `export_embedded_piece_pngs.py` | Extracted embedded piece PNG byte arrays out of Java source. | One-off asset tooling; the shared core owns piece rendering. |
| `generate_vector_piece_svgs.py` | Traced piece PNGs into SVGs with Potrace. | One-off asset tooling; use `fen render --format svg` for board/position SVG output. |
| `generate_whitesur_rook_logo.py` | Generated WhiteSur-style rook logo PNG/ICO assets. | One-off branding asset generation; no CLI replacement. |
| `run_svg_render_self_test.sh` | Compiled and ran the `utility.Svg` self-test in isolation. | Covered by the regression suite (`scripts/run_regression_suite.sh`). |

## Notes

- Everything in this directory except this `README.md` is ignored by git; restored copies are reference snapshots, not maintained code.
- Commands referenced above are documented and reproducible. The T5 summaries (`fen text`, `puzzle text`) run in-process and replace every hosted-model dependency the old scripts carried.
- Keep supported install, release, validation, and documented workflow scripts in `scripts/`, not here.

## Related pages

- [Command Reference](command-reference.md)
- [Command Cheatsheet](command-cheatsheet.md)
- [Build and Install](build-and-install.md)
- [Quality and Testing](quality-and-testing.md)

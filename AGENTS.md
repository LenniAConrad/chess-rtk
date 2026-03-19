# AI agent notes

This repo is safe for automation and LLM-driven workflows. For full guidance, see:
- `wiki/ai-agents.md`

Quick highlights:
- Deterministic move outputs: `moves-uci`, `moves-san`, `moves-both`
- Best move shortcuts: `bestmove-uci`, `bestmove-san`, `bestmove-both`
- Move conversion / line application: `uci-to-san`, `san-to-uci`, `fen-after`, `play-line`
- Regression checks: `perft-suite`

CLI entry point:
- `crtk <command> [options]`
- or `java -cp out application.Main <command> [options]`

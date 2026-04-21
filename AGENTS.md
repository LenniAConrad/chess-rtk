# AI agent notes

This repo is safe for automation and LLM-driven workflows. For full guidance, see:
- `wiki/ai-agents.md`

Quick highlights:
- Deterministic move outputs: `move list --format uci|san|both`, `move uci`, `move san`, `move both`
- Best move shortcuts: `engine bestmove --format uci|san|both`, `engine bestmove-uci`, `engine bestmove-san`, `engine bestmove-both`
- Move conversion / line application: `move to-san`, `move to-uci`, `move after`, `move play`
- FEN and setup helpers: `fen normalize`, `fen validate`, `fen chess960`
- Regression checks: `engine perft-suite`

CLI entry point:
- `crtk <command> [options]`
- or `java -cp out application.Main <command> [options]`

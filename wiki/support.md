# Support

This page describes the information that makes ChessRTK issues actionable.

## Before Reporting

Run the local health checks that match the failure:

```bash
crtk doctor
crtk config validate
./scripts/run_regression_suite.sh recommended
```

For UCI engine problems:

```bash
crtk engine uci-smoke --nodes 1 --max-duration 5s --verbose
```

For move-generation or notation problems:

```bash
crtk engine perft-suite --depth 6 --threads 4
crtk move list --fen "<FEN>" --format both
```

## Include This In Bug Reports

- exact command line
- full FEN, PGN snippet, or input file shape
- expected behavior
- actual output or stack trace
- Java version from `java -version`
- operating system
- whether the launcher or `java -cp out application.Main` was used
- external engine path and protocol TOML when UCI is involved
- whether `./scripts/run_regression_suite.sh recommended` passes

## Common Places To Start

- Setup failure: [Build and install](build-and-install.md)
- Engine failure: [Configuration](configuration.md)
- Command syntax: [Command reference](command-reference.md)
- Model files: [LC0](lc0.md), [T5](t5.md), and `models/README.md`
- Generated outputs: [Outputs and logs](outputs-and-logs.md)
- Known operational issues: [Troubleshooting](troubleshooting.md)

## Security Notes

Do not paste private engine paths, private dataset paths, unpublished book
manuscripts, or proprietary model locations unless they are needed and safe to
share. Replace them with equivalent local examples where possible.

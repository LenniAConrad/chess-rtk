# Lc0 (UCI weights + Java evaluator)

This repo contains two separate “Lc0” concerns:

1) Using the **Lc0 UCI engine** for mining (needs `.pb.gz` weights).
2) Using the **built-in Java LC0 evaluator** for fast local evaluation/ablation (needs an `LC0J` `.bin`).

## 1) Lc0 as a UCI engine (mining)

If your engine protocol points to `lc0` (see `config/lc0.engine.toml`), you typically also need to set:

```toml
setup = [
  # ...
  "setoption name WeightsFile value /path/to/network.pb.gz",
]
```

Recommended workflow:

1. Download a network locally (gitignored):

   ```bash
   ./scripts/fetch_lc0_net.sh --url <URL> --out nets
   ```

2. Update your engine protocol TOML to point `WeightsFile` at that path.
3. (Optional) switch mining thresholds to the included `config/cli.lc0.config.toml` baseline (copy it over `config/cli.config.toml`).

Guardrail (fail if weights are tracked by git):

```bash
./scripts/check_no_weights_tracked.sh
```

## 2) Built-in Java LC0 evaluator (display/ablation)

The Java evaluator lives under `src/chess/lc0/` and is used by `chess.eval.Evaluator`.

Defaults:
- weights: `models/lc0_744706.bin` (`chess.lc0.Model.DEFAULT_WEIGHTS`)
- backend: `cpu` unless the optional CUDA JNI backend is available

Backend selection (system properties):
- `-Ducicli.lc0.backend=auto|cpu|cuda` (default `auto`)
- `-Ducicli.lc0.threads=N` (CPU backend only)

To see which backend is being used in practice (opens a window), run:

```bash
ucicli display --fen "<FEN>" --show-backend
```

### CUDA backend (optional)

Build the JNI library:

```bash
cmake -S native/cuda -B native/cuda/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/cuda/build -j
```

Then run Java with the library on `java.library.path`:

```bash
java -cp out -Djava.library.path=native/cuda/build -Ducicli.lc0.backend=cuda application.Main display --fen "<FEN>" --show-backend
```

See `native/cuda/README.md` for more details.

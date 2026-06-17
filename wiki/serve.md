# Local JSON-RPC Daemon

`crtk serve` starts a localhost-only HTTP wrapper around the existing command dispatcher. It is for agents, editors, and scripts that need many small `crtk` calls without paying JVM startup every time. It is not an online service, not a play server, and not a social or matchmaking endpoint.

## Start It

```bash
crtk serve --port 8787
```

The daemon binds `127.0.0.1` by default and refuses non-loopback addresses. Use `--port 0` when embedding or testing and let the OS choose an ephemeral port.

```bash
crtk serve --host 127.0.0.1 --port 8787 --json
```

## Endpoints

| Endpoint | Method | Contract |
| --- | --- | --- |
| `/health` | `GET` | daemon status, schema, host, and port |
| `/catalog` | `GET` | the same deterministic `crtk help --json` catalog |
| `/rpc` | `POST` | JSON-RPC command dispatch through `application.Main` |

Unknown paths return 404. There are no game-play, matchmaking, account, network-play, or remote-analysis endpoints.

## JSON-RPC Run

Send `method: "run"` with `params.argv` as the exact CLI argv tokens after `crtk`.

```bash
curl -s http://127.0.0.1:8787/rpc \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"run","params":{"argv":["fen","validate","--fen","<FEN>","--json"]}}'
```

The response keeps stdout and stderr separate and reports the CLI exit code.

```json
{"jsonrpc":"2.0","id":1,"result":{"exitCode":0,"stdout":"...","stderr":"","argv":["fen","validate","--fen","<FEN>","--json"]}}
```

Commands still run through the normal `application.Main` path, so JSON shapes, exit codes, and diagnostics match the CLI. Because stdout and stderr are process-wide streams, the daemon serializes requests through one dispatcher thread.

## Discovery

Fetch the catalog instead of scraping help text:

```bash
curl -s http://127.0.0.1:8787/catalog
```

That response is the same `crtk.cli.catalog.v1` object emitted by:

```bash
crtk help --json
```

## Python SDK

The Python client is generated from the command catalog. Regenerate it after a
command is added, removed, or renamed:

```bash
python3 scripts/generate_python_sdk.py
```

The default output is `sdk/python/crtk_client.py`. The generator can also read a
saved catalog, which is useful for tests and release checks:

```bash
crtk help --json > /tmp/crtk-catalog.json
python3 scripts/generate_python_sdk.py --catalog /tmp/crtk-catalog.json
```

Use the client against a running daemon:

```python
from crtk_client import CrtkClient

fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
client = CrtkClient("http://127.0.0.1:8787")
result = client.fen_validate("--fen", fen, "--json", check=True)
print(result.stdout)
```

## Safety Boundaries

- The server binds only loopback addresses.
- The daemon is off by default and exists only when `crtk serve` is running.
- Engine work is still bounded by the command flags you pass, such as `--max-nodes`, `--max-duration`, and `--multipv`.
- Nested `serve` calls are rejected through the daemon to avoid blocking the dispatcher with another long-lived server.

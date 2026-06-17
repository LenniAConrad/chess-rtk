#!/usr/bin/env python3
"""Generate the thin Python client for the local crtk daemon."""

from __future__ import annotations

import argparse
import json
import keyword
import re
import subprocess
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT = Path("sdk/python/crtk_client.py")
DEFAULT_CATALOG_COMMAND = ["java", "-cp", "out", "application.Main", "help", "--json"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, help="Read an existing help --json catalog")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="Generated client path")
    parser.add_argument(
        "--catalog-command",
        nargs=argparse.REMAINDER,
        help="Command that prints the catalog; default: java -cp out application.Main help --json",
    )
    args = parser.parse_args()

    catalog = load_catalog(args.catalog, args.catalog_command)
    text = render_client(catalog)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(text, encoding="utf-8")
    print(f"generated {args.output}")
    return 0


def load_catalog(path: Path | None, command: list[str] | None) -> dict[str, Any]:
    if path is not None:
        return json.loads(path.read_text(encoding="utf-8"))
    cmd = command if command else DEFAULT_CATALOG_COMMAND
    completed = subprocess.run(cmd, check=True, text=True, stdout=subprocess.PIPE)
    return json.loads(completed.stdout)


def render_client(catalog: dict[str, Any]) -> str:
    commands = sorted(iter_runnable_commands(catalog.get("commands", [])), key=lambda item: item["path"])
    seen: dict[str, int] = {}
    methods: list[str] = []
    for command in commands:
        method = unique_method_name(method_name(command["path"]), seen)
        methods.append(render_method(method, command))
    return "\n".join(
        [
            '"""Generated Python client for the crtk localhost daemon.',
            "",
            "Regenerate with scripts/generate_python_sdk.py. Do not hand-edit.",
            '"""',
            "",
            "from __future__ import annotations",
            "",
            "from dataclasses import dataclass",
            "import json",
            "from typing import Any, Sequence",
            "from urllib import request",
            "",
            "",
            f"SCHEMA_VERSION = {catalog.get('schemaVersion', '')!r}",
            f"GENERATED_COMMANDS = {len(commands)}",
            "",
            "",
            "@dataclass(frozen=True)",
            "class CrtkResult:",
            '    """Result of one daemon-backed CLI invocation."""',
            "",
            "    exit_code: int",
            "    stdout: str",
            "    stderr: str",
            "    argv: tuple[str, ...]",
            "",
            "",
            "class CrtkCommandError(RuntimeError):",
            '    """Raised when check=True and a command exits non-zero."""',
            "",
            "    def __init__(self, result: CrtkResult) -> None:",
            "        super().__init__(result.stderr or f'crtk command failed with exit code {result.exit_code}')",
            "        self.result = result",
            "",
            "",
            "class CrtkClient:",
            '    """Thin JSON-RPC client for crtk serve."""',
            "",
            "    def __init__(self, base_url: str = 'http://127.0.0.1:8787', timeout: float = 30.0) -> None:",
            "        self.base_url = base_url.rstrip('/')",
            "        self.timeout = timeout",
            "        self._next_id = 1",
            "",
            "    def health(self) -> dict[str, Any]:",
            '        """Return daemon health metadata."""',
            "",
            "        return self._get_json('/health')",
            "",
            "    def catalog(self) -> dict[str, Any]:",
            '        """Return the live crtk.cli.catalog.v1 command catalog."""',
            "",
            "        return self._get_json('/catalog')",
            "",
            "    def run(self, argv: Sequence[str], *, check: bool = False) -> CrtkResult:",
            '        """Run one crtk argv vector through the daemon."""',
            "",
            "        body = {",
            "            'jsonrpc': '2.0',",
            "            'id': self._next_id,",
            "            'method': 'run',",
            "            'params': {'argv': [str(part) for part in argv]},",
            "        }",
            "        self._next_id += 1",
            "        envelope = self._post_json('/rpc', body)",
            "        if 'error' in envelope:",
            "            raise RuntimeError(envelope['error'].get('message', 'crtk daemon error'))",
            "        raw = envelope['result']",
            "        result = CrtkResult(",
            "            exit_code=int(raw['exitCode']),",
            "            stdout=str(raw.get('stdout', '')),",
            "            stderr=str(raw.get('stderr', '')),",
            "            argv=tuple(str(part) for part in raw.get('argv', [])),",
            "        )",
            "        if check and result.exit_code != 0:",
            "            raise CrtkCommandError(result)",
            "        return result",
            "",
            *methods,
            "    def _get_json(self, path: str) -> dict[str, Any]:",
            "        with request.urlopen(self.base_url + path, timeout=self.timeout) as response:",
            "            return json.loads(response.read().decode('utf-8'))",
            "",
            "    def _post_json(self, path: str, body: dict[str, Any]) -> dict[str, Any]:",
            "        data = json.dumps(body, separators=(',', ':')).encode('utf-8')",
            "        req = request.Request(",
            "            self.base_url + path,",
            "            data=data,",
            "            headers={'Content-Type': 'application/json'},",
            "            method='POST',",
            "        )",
            "        with request.urlopen(req, timeout=self.timeout) as response:",
            "            return json.loads(response.read().decode('utf-8'))",
            "",
            "",
            "__all__ = ['CrtkClient', 'CrtkCommandError', 'CrtkResult', 'GENERATED_COMMANDS', 'SCHEMA_VERSION']",
            "",
        ]
    )


def iter_runnable_commands(nodes: list[dict[str, Any]]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for node in nodes:
        if node.get("runnable"):
            out.append(node)
        out.extend(iter_runnable_commands(node.get("children", [])))
    return out


def method_name(path: str) -> str:
    name = re.sub(r"[^0-9A-Za-z]+", "_", path.strip().lower()).strip("_")
    if not name:
        name = "command"
    if name[0].isdigit():
        name = "cmd_" + name
    if keyword.iskeyword(name):
        name += "_"
    return name


def unique_method_name(name: str, seen: dict[str, int]) -> str:
    count = seen.get(name, 0)
    seen[name] = count + 1
    return name if count == 0 else f"{name}_{count + 1}"


def render_method(method: str, command: dict[str, Any]) -> str:
    path = command["path"]
    argv = path.split()
    summary = str(command.get("summary") or "")
    return "\n".join(
        [
            f"    def {method}(self, *args: str, check: bool = False) -> CrtkResult:",
            f'        """{escape_docstring(summary)}"""',
            "",
            f"        return self.run({argv!r} + [str(arg) for arg in args], check=check)",
            "",
        ]
    )


def escape_docstring(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace('"""', r"\"\"\"")
        .replace("\r", r"\r")
        .replace("\n", r"\n")
    )


if __name__ == "__main__":
    raise SystemExit(main())

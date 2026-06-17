"""Generated Python client for the crtk localhost daemon.

Regenerate with scripts/generate_python_sdk.py. Do not hand-edit.
"""

from __future__ import annotations

from dataclasses import dataclass
import json
from typing import Any, Sequence
from urllib import request


SCHEMA_VERSION = 'crtk.cli.catalog.v1'
GENERATED_COMMANDS = 105


@dataclass(frozen=True)
class CrtkResult:
    """Result of one daemon-backed CLI invocation."""

    exit_code: int
    stdout: str
    stderr: str
    argv: tuple[str, ...]


class CrtkCommandError(RuntimeError):
    """Raised when check=True and a command exits non-zero."""

    def __init__(self, result: CrtkResult) -> None:
        super().__init__(result.stderr or f'crtk command failed with exit code {result.exit_code}')
        self.result = result


class CrtkClient:
    """Thin JSON-RPC client for crtk serve."""

    def __init__(self, base_url: str = 'http://127.0.0.1:8787', timeout: float = 30.0) -> None:
        self.base_url = base_url.rstrip('/')
        self.timeout = timeout
        self._next_id = 1

    def health(self) -> dict[str, Any]:
        """Return daemon health metadata."""

        return self._get_json('/health')

    def catalog(self) -> dict[str, Any]:
        """Return the live crtk.cli.catalog.v1 command catalog."""

        return self._get_json('/catalog')

    def run(self, argv: Sequence[str], *, check: bool = False) -> CrtkResult:
        """Run one crtk argv vector through the daemon."""

        body = {
            'jsonrpc': '2.0',
            'id': self._next_id,
            'method': 'run',
            'params': {'argv': [str(part) for part in argv]},
        }
        self._next_id += 1
        envelope = self._post_json('/rpc', body)
        if 'error' in envelope:
            raise RuntimeError(envelope['error'].get('message', 'crtk daemon error'))
        raw = envelope['result']
        result = CrtkResult(
            exit_code=int(raw['exitCode']),
            stdout=str(raw.get('stdout', '')),
            stderr=str(raw.get('stderr', '')),
            argv=tuple(str(part) for part in raw.get('argv', [])),
        )
        if check and result.exit_code != 0:
            raise CrtkCommandError(result)
        return result

    def batch_run(self, *args: str, check: bool = False) -> CrtkResult:
        """Run one ChessRTK command per script line"""

        return self.run(['batch', 'run'] + [str(arg) for arg in args], check=check)

    def book_collection(self, *args: str, check: bool = False) -> CrtkResult:
        """Build a dense puzzle collection from record JSON/JSONL"""

        return self.run(['book', 'collection'] + [str(arg) for arg in args], check=check)

    def book_cover(self, *args: str, check: bool = False) -> CrtkResult:
        """Render a native PDF cover for a chess-book file"""

        return self.run(['book', 'cover'] + [str(arg) for arg in args], check=check)

    def book_pdf(self, *args: str, check: bool = False) -> CrtkResult:
        """Export chess diagrams to a PDF"""

        return self.run(['book', 'pdf'] + [str(arg) for arg in args], check=check)

    def book_render(self, *args: str, check: bool = False) -> CrtkResult:
        """Render a chess-book JSON/TOML file to a native PDF"""

        return self.run(['book', 'render'] + [str(arg) for arg in args], check=check)

    def book_study(self, *args: str, check: bool = False) -> CrtkResult:
        """Render deeply annotated puzzle studies from a rich JSON/TOML manifest"""

        return self.run(['book', 'study'] + [str(arg) for arg in args], check=check)

    def clean(self, *args: str, check: bool = False) -> CrtkResult:
        """Delete session cache/logs"""

        return self.run(['clean'] + [str(arg) for arg in args], check=check)

    def config_show(self, *args: str, check: bool = False) -> CrtkResult:
        """Print config values"""

        return self.run(['config', 'show'] + [str(arg) for arg in args], check=check)

    def config_validate(self, *args: str, check: bool = False) -> CrtkResult:
        """Validate config file"""

        return self.run(['config', 'validate'] + [str(arg) for arg in args], check=check)

    def dataset_audit(self, *args: str, check: bool = False) -> CrtkResult:
        """Recursively audit every manifest under a directory"""

        return self.run(['dataset', 'audit'] + [str(arg) for arg in args], check=check)

    def dataset_diff(self, *args: str, check: bool = False) -> CrtkResult:
        """Explain why two manifests differ"""

        return self.run(['dataset', 'diff'] + [str(arg) for arg in args], check=check)

    def dataset_verify(self, *args: str, check: bool = False) -> CrtkResult:
        """Re-hash every artifact referenced by a manifest"""

        return self.run(['dataset', 'verify'] + [str(arg) for arg in args], check=check)

    def doctor(self, *args: str, check: bool = False) -> CrtkResult:
        """Check Java, config, protocol, engine, and local artifacts"""

        return self.run(['doctor'] + [str(arg) for arg in args], check=check)

    def engine_analyze(self, *args: str, check: bool = False) -> CrtkResult:
        """Analyze a position with the engine"""

        return self.run(['engine', 'analyze'] + [str(arg) for arg in args], check=check)

    def engine_analyze_batch(self, *args: str, check: bool = False) -> CrtkResult:
        """Analyze FEN batches as JSONL"""

        return self.run(['engine', 'analyze-batch'] + [str(arg) for arg in args], check=check)

    def engine_benchmark(self, *args: str, check: bool = False) -> CrtkResult:
        """Benchmark the core Java move generator"""

        return self.run(['engine', 'benchmark'] + [str(arg) for arg in args], check=check)

    def engine_bestmove(self, *args: str, check: bool = False) -> CrtkResult:
        """Print the best move for a position"""

        return self.run(['engine', 'bestmove'] + [str(arg) for arg in args], check=check)

    def engine_bestmove_batch(self, *args: str, check: bool = False) -> CrtkResult:
        """Find best moves for FEN batches as JSONL"""

        return self.run(['engine', 'bestmove-batch'] + [str(arg) for arg in args], check=check)

    def engine_bestmove_both(self, *args: str, check: bool = False) -> CrtkResult:
        """Print the best move in UCI and SAN"""

        return self.run(['engine', 'bestmove-both'] + [str(arg) for arg in args], check=check)

    def engine_bestmove_san(self, *args: str, check: bool = False) -> CrtkResult:
        """Print the best move in SAN"""

        return self.run(['engine', 'bestmove-san'] + [str(arg) for arg in args], check=check)

    def engine_bestmove_uci(self, *args: str, check: bool = False) -> CrtkResult:
        """Print the best move in UCI"""

        return self.run(['engine', 'bestmove-uci'] + [str(arg) for arg in args], check=check)

    def engine_builtin(self, *args: str, check: bool = False) -> CrtkResult:
        """Search with the built-in engine"""

        return self.run(['engine', 'builtin'] + [str(arg) for arg in args], check=check)

    def engine_compare(self, *args: str, check: bool = False) -> CrtkResult:
        """Compare best moves from two UCI protocols"""

        return self.run(['engine', 'compare'] + [str(arg) for arg in args], check=check)

    def engine_eval(self, *args: str, check: bool = False) -> CrtkResult:
        """Evaluate a position with LC0, OTIS, or classical"""

        return self.run(['engine', 'eval'] + [str(arg) for arg in args], check=check)

    def engine_gauntlet(self, *args: str, check: bool = False) -> CrtkResult:
        """Run a deterministic self-play A/B engine gauntlet"""

        return self.run(['engine', 'gauntlet'] + [str(arg) for arg in args], check=check)

    def engine_gpu(self, *args: str, check: bool = False) -> CrtkResult:
        """Print GPU JNI backend status"""

        return self.run(['engine', 'gpu'] + [str(arg) for arg in args], check=check)

    def engine_mate(self, *args: str, check: bool = False) -> CrtkResult:
        """Brute-force prove a forced mate without NN evaluation"""

        return self.run(['engine', 'mate'] + [str(arg) for arg in args], check=check)

    def engine_perft(self, *args: str, check: bool = False) -> CrtkResult:
        """Run perft on a position"""

        return self.run(['engine', 'perft'] + [str(arg) for arg in args], check=check)

    def engine_perft_suite(self, *args: str, check: bool = False) -> CrtkResult:
        """Run a small perft regression suite"""

        return self.run(['engine', 'perft-suite'] + [str(arg) for arg in args], check=check)

    def engine_search(self, *args: str, check: bool = False) -> CrtkResult:
        """Run a PUCT search and print root-move statistics"""

        return self.run(['engine', 'search'] + [str(arg) for arg in args], check=check)

    def engine_static(self, *args: str, check: bool = False) -> CrtkResult:
        """Evaluate a position with the classical backend"""

        return self.run(['engine', 'static'] + [str(arg) for arg in args], check=check)

    def engine_threats(self, *args: str, check: bool = False) -> CrtkResult:
        """Analyze opponent threats"""

        return self.run(['engine', 'threats'] + [str(arg) for arg in args], check=check)

    def engine_trace(self, *args: str, check: bool = False) -> CrtkResult:
        """Trace a neural evaluation: value, WDL, policy, and layers"""

        return self.run(['engine', 'trace'] + [str(arg) for arg in args], check=check)

    def engine_tree(self, *args: str, check: bool = False) -> CrtkResult:
        """Run a PUCT search and dump the search tree"""

        return self.run(['engine', 'tree'] + [str(arg) for arg in args], check=check)

    def engine_uci_smoke(self, *args: str, check: bool = False) -> CrtkResult:
        """Start engine and run a tiny UCI search"""

        return self.run(['engine', 'uci-smoke'] + [str(arg) for arg in args], check=check)

    def fen_after(self, *args: str, check: bool = False) -> CrtkResult:
        """Apply one move and print the resulting FEN"""

        return self.run(['fen', 'after'] + [str(arg) for arg in args], check=check)

    def fen_chess960(self, *args: str, check: bool = False) -> CrtkResult:
        """Print Chess960 starting positions by index or range"""

        return self.run(['fen', 'chess960'] + [str(arg) for arg in args], check=check)

    def fen_display(self, *args: str, check: bool = False) -> CrtkResult:
        """Render a position in a window"""

        return self.run(['fen', 'display'] + [str(arg) for arg in args], check=check)

    def fen_generate(self, *args: str, check: bool = False) -> CrtkResult:
        """Generate random legal FEN shards"""

        return self.run(['fen', 'generate'] + [str(arg) for arg in args], check=check)

    def fen_line(self, *args: str, check: bool = False) -> CrtkResult:
        """Apply a move line and print the resulting FEN"""

        return self.run(['fen', 'line'] + [str(arg) for arg in args], check=check)

    def fen_normalize(self, *args: str, check: bool = False) -> CrtkResult:
        """Normalize and validate a FEN"""

        return self.run(['fen', 'normalize'] + [str(arg) for arg in args], check=check)

    def fen_pgn(self, *args: str, check: bool = False) -> CrtkResult:
        """Convert PGN games to FEN lists"""

        return self.run(['fen', 'pgn'] + [str(arg) for arg in args], check=check)

    def fen_print(self, *args: str, check: bool = False) -> CrtkResult:
        """Pretty-print a position"""

        return self.run(['fen', 'print'] + [str(arg) for arg in args], check=check)

    def fen_relations(self, *args: str, check: bool = False) -> CrtkResult:
        """Render the OTIS tactical-incidence relation channels"""

        return self.run(['fen', 'relations'] + [str(arg) for arg in args], check=check)

    def fen_render(self, *args: str, check: bool = False) -> CrtkResult:
        """Save a position image to disk"""

        return self.run(['fen', 'render'] + [str(arg) for arg in args], check=check)

    def fen_tags(self, *args: str, check: bool = False) -> CrtkResult:
        """Generate tags for FENs, PGNs, or variations"""

        return self.run(['fen', 'tags'] + [str(arg) for arg in args], check=check)

    def fen_text(self, *args: str, check: bool = False) -> CrtkResult:
        """Summarize position tags with T5"""

        return self.run(['fen', 'text'] + [str(arg) for arg in args], check=check)

    def fen_validate(self, *args: str, check: bool = False) -> CrtkResult:
        """Validate a FEN"""

        return self.run(['fen', 'validate'] + [str(arg) for arg in args], check=check)

    def gauntlet(self, *args: str, check: bool = False) -> CrtkResult:
        """Run a deterministic self-play A/B engine gauntlet"""

        return self.run(['gauntlet'] + [str(arg) for arg in args], check=check)

    def gen_fens(self, *args: str, check: bool = False) -> CrtkResult:
        """Alias for `fen generate`"""

        return self.run(['gen', 'fens'] + [str(arg) for arg in args], check=check)

    def help(self, *args: str, check: bool = False) -> CrtkResult:
        """Show command help"""

        return self.run(['help'] + [str(arg) for arg in args], check=check)

    def mate(self, *args: str, check: bool = False) -> CrtkResult:
        """Brute-force prove a forced mate without NN evaluation"""

        return self.run(['mate'] + [str(arg) for arg in args], check=check)

    def move_after(self, *args: str, check: bool = False) -> CrtkResult:
        """Apply one move and print the resulting FEN"""

        return self.run(['move', 'after'] + [str(arg) for arg in args], check=check)

    def move_both(self, *args: str, check: bool = False) -> CrtkResult:
        """List legal moves in UCI and SAN"""

        return self.run(['move', 'both'] + [str(arg) for arg in args], check=check)

    def move_list(self, *args: str, check: bool = False) -> CrtkResult:
        """List legal moves for a position"""

        return self.run(['move', 'list'] + [str(arg) for arg in args], check=check)

    def move_play(self, *args: str, check: bool = False) -> CrtkResult:
        """Apply a move line and print the resulting FEN"""

        return self.run(['move', 'play'] + [str(arg) for arg in args], check=check)

    def move_san(self, *args: str, check: bool = False) -> CrtkResult:
        """List legal moves in SAN"""

        return self.run(['move', 'san'] + [str(arg) for arg in args], check=check)

    def move_to_san(self, *args: str, check: bool = False) -> CrtkResult:
        """Convert one UCI move to SAN"""

        return self.run(['move', 'to-san'] + [str(arg) for arg in args], check=check)

    def move_to_uci(self, *args: str, check: bool = False) -> CrtkResult:
        """Convert one SAN move to UCI"""

        return self.run(['move', 'to-uci'] + [str(arg) for arg in args], check=check)

    def move_uci(self, *args: str, check: bool = False) -> CrtkResult:
        """List legal moves in UCI"""

        return self.run(['move', 'uci'] + [str(arg) for arg in args], check=check)

    def pgn_compact(self, *args: str, check: bool = False) -> CrtkResult:
        """Drop tombstoned games and rebuild indexes (mutating)"""

        return self.run(['pgn', 'compact'] + [str(arg) for arg in args], check=check)

    def pgn_delete(self, *args: str, check: bool = False) -> CrtkResult:
        """Tombstone one stored game by id (mutating)"""

        return self.run(['pgn', 'delete'] + [str(arg) for arg in args], check=check)

    def pgn_find(self, *args: str, check: bool = False) -> CrtkResult:
        """Find games that pass through a given FEN"""

        return self.run(['pgn', 'find'] + [str(arg) for arg in args], check=check)

    def pgn_import(self, *args: str, check: bool = False) -> CrtkResult:
        """Import games from a PGN file (idempotent)"""

        return self.run(['pgn', 'import'] + [str(arg) for arg in args], check=check)

    def pgn_show(self, *args: str, check: bool = False) -> CrtkResult:
        """Show one stored game by id"""

        return self.run(['pgn', 'show'] + [str(arg) for arg in args], check=check)

    def pgn_stats(self, *args: str, check: bool = False) -> CrtkResult:
        """Summarize the store"""

        return self.run(['pgn', 'stats'] + [str(arg) for arg in args], check=check)

    def position_describe(self, *args: str, check: bool = False) -> CrtkResult:
        """Describe a position with deterministic text"""

        return self.run(['position', 'describe'] + [str(arg) for arg in args], check=check)

    def position_diff(self, *args: str, check: bool = False) -> CrtkResult:
        """Compare two FEN positions"""

        return self.run(['position', 'diff'] + [str(arg) for arg in args], check=check)

    def puzzle_mine(self, *args: str, check: bool = False) -> CrtkResult:
        """Mine chess puzzles"""

        return self.run(['puzzle', 'mine'] + [str(arg) for arg in args], check=check)

    def puzzle_pgn(self, *args: str, check: bool = False) -> CrtkResult:
        """Convert mixed puzzle dumps to PGN games"""

        return self.run(['puzzle', 'pgn'] + [str(arg) for arg in args], check=check)

    def puzzle_tags(self, *args: str, check: bool = False) -> CrtkResult:
        """Generate per-move tags for puzzle PVs"""

        return self.run(['puzzle', 'tags'] + [str(arg) for arg in args], check=check)

    def puzzle_text(self, *args: str, check: bool = False) -> CrtkResult:
        """Run T5 over puzzle PVs"""

        return self.run(['puzzle', 'text'] + [str(arg) for arg in args], check=check)

    def record_analysis_delta(self, *args: str, check: bool = False) -> CrtkResult:
        """Compare parent/child analysis changes"""

        return self.run(['record', 'analysis-delta'] + [str(arg) for arg in args], check=check)

    def record_audit_split(self, *args: str, check: bool = False) -> CrtkResult:
        """Detect position leakage across record splits"""

        return self.run(['record', 'audit-split'] + [str(arg) for arg in args], check=check)

    def record_classifier(self, *args: str, check: bool = False) -> CrtkResult:
        """Alias for `record dataset classifier`"""

        return self.run(['record', 'classifier'] + [str(arg) for arg in args], check=check)

    def record_csv(self, *args: str, check: bool = False) -> CrtkResult:
        """Alias for `record export csv`"""

        return self.run(['record', 'csv'] + [str(arg) for arg in args], check=check)

    def record_dataset(self, *args: str, check: bool = False) -> CrtkResult:
        """Export tensors as npy, lc0, or classifier"""

        return self.run(['record', 'dataset'] + [str(arg) for arg in args], check=check)

    def record_dataset_classifier(self, *args: str, check: bool = False) -> CrtkResult:
        """Convert .record JSON to classifier tensors"""

        return self.run(['record', 'dataset', 'classifier'] + [str(arg) for arg in args], check=check)

    def record_dataset_lc0(self, *args: str, check: bool = False) -> CrtkResult:
        """Convert .record JSON to LC0 tensors"""

        return self.run(['record', 'dataset', 'lc0'] + [str(arg) for arg in args], check=check)

    def record_dataset_npy(self, *args: str, check: bool = False) -> CrtkResult:
        """Convert .record JSON to NPY tensors"""

        return self.run(['record', 'dataset', 'npy'] + [str(arg) for arg in args], check=check)

    def record_dedupe(self, *args: str, check: bool = False) -> CrtkResult:
        """Remove duplicate record rows before split/export"""

        return self.run(['record', 'dedupe'] + [str(arg) for arg in args], check=check)

    def record_export_csv(self, *args: str, check: bool = False) -> CrtkResult:
        """Convert .record JSON to CSV"""

        return self.run(['record', 'export', 'csv'] + [str(arg) for arg in args], check=check)

    def record_export_pgn(self, *args: str, check: bool = False) -> CrtkResult:
        """Convert .record JSON to PGN games"""

        return self.run(['record', 'export', 'pgn'] + [str(arg) for arg in args], check=check)

    def record_export_plain(self, *args: str, check: bool = False) -> CrtkResult:
        """Convert .record JSON to .plain"""

        return self.run(['record', 'export', 'plain'] + [str(arg) for arg in args], check=check)

    def record_export_puzzle_elo_jsonl(self, *args: str, check: bool = False) -> CrtkResult:
        """Export verified puzzle records with Elo tags"""

        return self.run(['record', 'export', 'puzzle-elo-jsonl'] + [str(arg) for arg in args], check=check)

    def record_export_puzzle_jsonl(self, *args: str, check: bool = False) -> CrtkResult:
        """Export verified puzzle rows as JSONL"""

        return self.run(['record', 'export', 'puzzle-jsonl'] + [str(arg) for arg in args], check=check)

    def record_export_training_jsonl(self, *args: str, check: bool = False) -> CrtkResult:
        """Export FEN JSONL labels for training"""

        return self.run(['record', 'export', 'training-jsonl'] + [str(arg) for arg in args], check=check)

    def record_files(self, *args: str, check: bool = False) -> CrtkResult:
        """Merge, filter, or split record files"""

        return self.run(['record', 'files'] + [str(arg) for arg in args], check=check)

    def record_lc0(self, *args: str, check: bool = False) -> CrtkResult:
        """Alias for `record dataset lc0`"""

        return self.run(['record', 'lc0'] + [str(arg) for arg in args], check=check)

    def record_npy(self, *args: str, check: bool = False) -> CrtkResult:
        """Alias for `record dataset npy`"""

        return self.run(['record', 'npy'] + [str(arg) for arg in args], check=check)

    def record_pgn(self, *args: str, check: bool = False) -> CrtkResult:
        """Alias for `record export pgn`"""

        return self.run(['record', 'pgn'] + [str(arg) for arg in args], check=check)

    def record_plain(self, *args: str, check: bool = False) -> CrtkResult:
        """Alias for `record export plain`"""

        return self.run(['record', 'plain'] + [str(arg) for arg in args], check=check)

    def record_puzzle_jsonl(self, *args: str, check: bool = False) -> CrtkResult:
        """Alias for `record export puzzle-jsonl`"""

        return self.run(['record', 'puzzle-jsonl'] + [str(arg) for arg in args], check=check)

    def record_split(self, *args: str, check: bool = False) -> CrtkResult:
        """Deterministic group-aware train/val/test split"""

        return self.run(['record', 'split'] + [str(arg) for arg in args], check=check)

    def record_stats(self, *args: str, check: bool = False) -> CrtkResult:
        """Summarize record files"""

        return self.run(['record', 'stats'] + [str(arg) for arg in args], check=check)

    def record_tag_stats(self, *args: str, check: bool = False) -> CrtkResult:
        """Summarize tag distributions"""

        return self.run(['record', 'tag-stats'] + [str(arg) for arg in args], check=check)

    def record_training_jsonl(self, *args: str, check: bool = False) -> CrtkResult:
        """Alias for `record export training-jsonl`"""

        return self.run(['record', 'training-jsonl'] + [str(arg) for arg in args], check=check)

    def record_validate(self, *args: str, check: bool = False) -> CrtkResult:
        """Fail-loud validation of a record file"""

        return self.run(['record', 'validate'] + [str(arg) for arg in args], check=check)

    def review_game(self, *args: str, check: bool = False) -> CrtkResult:
        """Review PGN games as crtk.review.ply.v1 JSONL"""

        return self.run(['review', 'game'] + [str(arg) for arg in args], check=check)

    def schema_list(self, *args: str, check: bool = False) -> CrtkResult:
        """List registered schema names"""

        return self.run(['schema', 'list'] + [str(arg) for arg in args], check=check)

    def schema_show(self, *args: str, check: bool = False) -> CrtkResult:
        """Print a registered schema's source text"""

        return self.run(['schema', 'show'] + [str(arg) for arg in args], check=check)

    def schema_validate(self, *args: str, check: bool = False) -> CrtkResult:
        """Validate a JSON document against a registered schema"""

        return self.run(['schema', 'validate'] + [str(arg) for arg in args], check=check)

    def serve(self, *args: str, check: bool = False) -> CrtkResult:
        """Start a localhost-only JSON-RPC daemon"""

        return self.run(['serve'] + [str(arg) for arg in args], check=check)

    def version(self, *args: str, check: bool = False) -> CrtkResult:
        """Print ChessRTK version metadata"""

        return self.run(['version'] + [str(arg) for arg in args], check=check)

    def workbench(self, *args: str, check: bool = False) -> CrtkResult:
        """Launch the native command and analysis workbench"""

        return self.run(['workbench'] + [str(arg) for arg in args], check=check)

    def _get_json(self, path: str) -> dict[str, Any]:
        with request.urlopen(self.base_url + path, timeout=self.timeout) as response:
            return json.loads(response.read().decode('utf-8'))

    def _post_json(self, path: str, body: dict[str, Any]) -> dict[str, Any]:
        data = json.dumps(body, separators=(',', ':')).encode('utf-8')
        req = request.Request(
            self.base_url + path,
            data=data,
            headers={'Content-Type': 'application/json'},
            method='POST',
        )
        with request.urlopen(req, timeout=self.timeout) as response:
            return json.loads(response.read().decode('utf-8'))


__all__ = ['CrtkClient', 'CrtkCommandError', 'CrtkResult', 'GENERATED_COMMANDS', 'SCHEMA_VERSION']

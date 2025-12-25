# Filter DSL

Mining gates (and some converters) use a compact, round-trippable DSL implemented by `chess.uci.Filter`.

If you only want to tweak mining behavior, you generally only need:
- the list of supported predicates
- how `gate=...`, `break=...`, `null=...`, `empty=...` interact

## Shape

A filter is a tree of blocks. Each block:
- picks a MultiPV line to inspect (`break=<n>`, where 1 = PV1, 2 = PV2, …)
- evaluates predicates against that PV output
- optionally evaluates nested blocks (`leaf[ ... ]`)
- combines all booleans using `gate=<...>`

Separators:
- predicates/keys are delimited by `;`
- whitespace is ignored

## Core keys

- `gate=<AND|NOT_AND|OR|NOT_OR|XOR|X_NOT_OR|SAME|NOT_SAME>`
- `break=<n>`: which PV line to inspect (1 = best line)
- `null=<true|false>`: return value when PV data is missing
- `empty=<true|false>`: return value when the block has no predicates/leaves

## Predicates (keys)

Predicates compare an output attribute using one of: `>`, `>=`, `=`, `<=`, `<`.

Supported keys:
- `depth`, `seldepth`, `multipv`, `hashfull`
- `nodes`, `nps`, `tbhits`, `time` (ms)
- `eval` (centipawns or mate; decimals are allowed as pawns, e.g. `300` == `3.0`, `#-2`)
- `chances` (WDL), e.g. `wdl 790 200 10` or `1000/0/0`

## Examples

### “Quality”: demand enough effort on PV1 and PV2

```txt
gate=AND;null=false;empty=false;
leaf[gate=AND;break=1;nodes>=50000000];
leaf[gate=AND;break=2;null=false;empty=false;nodes>=50000000];
```

### “Winning puzzle”: best line clearly good, second line not

```txt
gate=AND;
leaf[eval>=300];
leaf[break=2;null=false;eval<=0];
```

### “Accelerate”: cheap reject to avoid wasting time

```txt
gate=AND;
leaf[break=1;nodes>=2000000];
leaf[break=2;null=false;nodes>=2000000];
leaf[gate=OR;eval<300;leaf[break=2;eval>0]];
leaf[gate=OR;eval<0;leaf[break=2;eval>-300]];
```

## Where filters live

- Default mining filters are stored in `config/cli.config.toml` as TOML multi-line strings.
- You can override them per run via `mine --puzzle-quality <dsl>`, etc.

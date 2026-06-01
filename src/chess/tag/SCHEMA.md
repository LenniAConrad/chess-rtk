# Position Tag Schema

The canonical, always-current tag schema reference lives at the repository's
developer tag-documentation hub, where its links resolve correctly:

- **Full developer spec:** [`tag/SCHEMA.md`](../../../tag/SCHEMA.md) — every tag
  family, field, identity key, and the per-move delta JSON shape, kept in sync
  with this `chess.tag` package.
- **User-facing reference:** [Tag Reference](../../../wiki/tag-reference.md) on the
  documentation site.

This package implements that schema. When you add or change a tag:

1. Register the family, key, and value string literals in
   `chess.tag.core.Literals`, and any new family order in `chess.tag.Sort`.
2. Update [`tag/SCHEMA.md`](../../../tag/SCHEMA.md) so the contract stays true.
3. Add or update a golden fixture under `testdata/tags/` and the relevant
   `TaggingRegressionTest` case.

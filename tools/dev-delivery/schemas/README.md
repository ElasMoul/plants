# Vendored contracts JSON Schemas (validation only)

Verbatim copies of three schemas from `contracts` tag **v0.38.0**, kept here so
`dev_delivery.py` can validate what it emits **offline**, in CI, and on a host
that has no `contracts` checkout. They are a validation oracle, not types: no
plantpal code imports anything from this directory, and nothing here is
hand-edited. The pydantic binding remains the only thing that shapes a document
(`platform_contracts`, pinned in `../requirements.txt`).

| File | Copied from (`contracts` @ `v0.38.0`) |
|---|---|
| `delivery/delivery.deployment-receipt.json` | `schemas/delivery/delivery.deployment-receipt.json` |
| `delivery/delivery.producer-result.json` | `schemas/delivery/delivery.producer-result.json` |
| `app/deployment-identity.json` | `schemas/app/deployment-identity.json` |

`delivery.producer-result.json` and `app/deployment-identity.json` are here
because the receipt schema `$ref`s them (`#/$defs/check`, `#/$defs/correlation`,
and `observed`); the `delivery/` + `app/` layout is preserved so those relative
`$ref`s resolve unchanged.

**Why these three and not the whole schema set:** the receipt is a closed shape
(`additionalProperties: false`), so validating it means resolving every `$ref`
it reaches and nothing else.

**Repin procedure** (do this in the same commit as the `requirements.txt` bump):

```bash
SRC=../contracts-worktrees/<tag>/schemas
cp "$SRC/delivery/delivery.deployment-receipt.json" \
   "$SRC/delivery/delivery.producer-result.json" schemas/delivery/
cp "$SRC/app/deployment-identity.json" schemas/app/
```

Then confirm they are byte-identical to the tag, and run
`python -m unittest discover -s tools/dev-delivery -p "test_*.py"`.

An out-of-tree schema directory overrides these copies for a local check:
`PLATFORM_CONTRACTS_SCHEMAS=<dir> dev_delivery.py receipt <id>`.

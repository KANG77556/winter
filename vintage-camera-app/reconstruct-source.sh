#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PARTS_DIR="$ROOT_DIR/source-parts"
OUTPUT_ZIP="$ROOT_DIR/VintageColorCamera-v3.0.0-source.zip"
EXPECTED_SHA256="473bc705bc0a7e8889d60620a56f00b1d20f23d6077c1c7fb4a3b5476248dc18"

cat "$PARTS_DIR"/part_*.b64 | base64 --decode > "$OUTPUT_ZIP"

echo "$EXPECTED_SHA256  $OUTPUT_ZIP" | sha256sum --check --status

echo "Source archive restored: $OUTPUT_ZIP"

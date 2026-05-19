#!/bin/sh
# Generate C source arrays from MuPDF URW base14 font files.
# Run from the repo root before ndk-build.

set -e

MUPDF_DIR="$(dirname "$0")/mupdf"
HEXDUMP_SRC="$MUPDF_DIR/scripts/hexdump.c"
HEXDUMP="$MUPDF_DIR/scripts/hexdump"
FONTS_DIR="$MUPDF_DIR/resources/fonts/urw"
OUT_DIR="$MUPDF_DIR/generated/resources/fonts/urw"

cc -o "$HEXDUMP" "$HEXDUMP_SRC"

mkdir -p "$OUT_DIR"
for f in "$FONTS_DIR"/*.cff; do
    out="$OUT_DIR/$(basename "$f").c"
    "$HEXDUMP" "$out" "$f"
done

rm -f "$HEXDUMP"
echo "Generated $(ls "$OUT_DIR"/*.c | wc -l) font files in $OUT_DIR"

#!/bin/sh
# Generate C source arrays from MuPDF font resource files.
# Run from the repo root before ndk-build.

set -e

MUPDF_DIR="$(dirname "$0")/mupdf"
HEXDUMP_SRC="$MUPDF_DIR/scripts/hexdump.c"
HEXDUMP="$MUPDF_DIR/scripts/hexdump"

cc -o "$HEXDUMP" "$HEXDUMP_SRC"

# URW base14 fonts
URW_DIR="$MUPDF_DIR/resources/fonts/urw"
URW_OUT="$MUPDF_DIR/generated/resources/fonts/urw"
mkdir -p "$URW_OUT"
for f in "$URW_DIR"/*.cff; do
    out="$URW_OUT/$(basename "$f").c"
    "$HEXDUMP" "$out" "$f"
done
echo "Generated $(ls "$URW_OUT"/*.c | wc -l) URW font files"

# CJK font (SourceHanSerif)
HAN_DIR="$MUPDF_DIR/resources/fonts/han"
HAN_OUT="$MUPDF_DIR/generated/resources/fonts/han"
mkdir -p "$HAN_OUT"
for f in "$HAN_DIR"/*.ttc; do
    out="$HAN_OUT/$(basename "$f").c"
    "$HEXDUMP" "$out" "$f"
done
echo "Generated $(ls "$HAN_OUT"/*.c | wc -l) CJK font files"

rm -f "$HEXDUMP"

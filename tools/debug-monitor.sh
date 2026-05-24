#!/bin/bash
#
# EbookNT debug monitor — crash + black-screen detection in one script.
#
#   Crash:        continuous logcat filtering for fatal signals / exceptions / ANR
#   Black screen: periodic screenshot + pixel analysis (every 5s, foreground only)
#
# On any event, diagnostics are dumped automatically:
#   screenshot, activity/window/gfxinfo state, thread traces, recent logcat.
#
# Usage:
#   ./tools/debug-monitor.sh              # default output dir ~/ebooknt-logs
#   ./tools/debug-monitor.sh /tmp/logs    # custom output dir
#
# Requires: adb.  Optional: python3 (accurate black-screen pixel analysis;
# without it, falls back to PNG file-size heuristic).

set -uo pipefail

PKG="org.ebooknt.viewer"
LOG_DIR="${1:-$HOME/ebooknt-logs}"
POLL_INTERVAL=5
BLACK_THRESHOLD_KB=25

mkdir -p "$LOG_DIR"
STAMP=$(date +%Y%m%d-%H%M%S)
RAW_LOG="$LOG_DIR/logcat-$STAMP.log"
EVENT_LOG="$LOG_DIR/events-$STAMP.log"
TMPSHOT=$(mktemp /tmp/ebooknt-screen-XXXXXX.png)

BG_PIDS=()

cleanup() {
    for pid in "${BG_PIDS[@]}"; do
        kill "$pid" 2>/dev/null
    done
    wait "${BG_PIDS[@]}" 2>/dev/null
    rm -f "$TMPSHOT"

    local raw_kb=0
    [ -f "$RAW_LOG" ] && raw_kb=$(( $(wc -c < "$RAW_LOG") / 1024 ))

    echo ""
    echo "=== Session ended ==="
    echo "Raw logcat : $RAW_LOG (${raw_kb} KB)"
    if [ -s "$EVENT_LOG" ]; then
        echo "Events     : $EVENT_LOG (issues found!)"
    else
        echo "No issues detected."
        rm -f "$EVENT_LOG"
    fi
}
trap cleanup EXIT INT TERM

log() {
    local ts
    ts=$(date '+%H:%M:%S')
    echo "[$ts] $*" | tee -a "$EVENT_LOG"
}

get_pid() {
    adb shell pidof "$PKG" 2>/dev/null | tr -d '\r'
}

is_foreground() {
    adb shell dumpsys activity activities 2>/dev/null \
        | grep -q "mResumedActivity.*$PKG"
}

# ── Black-screen detection ──────────────────────────────────────────

is_black_screen() {
    local file="$1"
    local size
    size=$(stat -c%s "$file" 2>/dev/null || echo 0)
    [ "$size" -lt 100 ] && return 1

    if command -v python3 &>/dev/null; then
        python3 -c "
import sys, struct, zlib
with open('$file', 'rb') as f:
    sig = f.read(8)
    if sig[:4] != b'\x89PNG':
        sys.exit(1)
    w = h = 0; idat = b''
    while True:
        hdr = f.read(8)
        if len(hdr) < 8: break
        ln = struct.unpack('>I', hdr[:4])[0]; ct = hdr[4:8]
        d = f.read(ln); f.read(4)
        if ct == b'IHDR': w, h = struct.unpack('>II', d[:8])
        elif ct == b'IDAT': idat += d
        elif ct == b'IEND': break
    raw = zlib.decompress(idat)
    bpp = (len(raw) - h) // (w * h) if w and h else 4
    stride = 1 + w * bpp
    dark = total = 0
    step = max(1, (w * h) // 1000)
    for i in range(0, w * h, step):
        r_i, c = divmod(i, w)
        off = r_i * stride + 1 + c * bpp
        if off + 2 < len(raw):
            total += 1
            if raw[off] < 25 and raw[off+1] < 25 and raw[off+2] < 25:
                dark += 1
    sys.exit(0 if total and dark / total > 0.92 else 1)
" 2>/dev/null
        return $?
    fi

    # Fallback: mostly-black PNGs compress very small
    [ "$((size / 1024))" -lt "$BLACK_THRESHOLD_KB" ]
}

# ── Diagnostics dump (shared by crash & black-screen paths) ─────────

dump_diagnostics() {
    local reason="$1"
    local tag
    tag=$(date +%H%M%S)
    local dump_dir="$LOG_DIR/dump-${reason}-${tag}"
    mkdir -p "$dump_dir"
    log "  Saving diagnostics → $dump_dir/"

    # Screenshot (best-effort, may already be stale for crash)
    adb exec-out screencap -p > "$dump_dir/screenshot.png" 2>/dev/null || true

    # Activity state
    adb shell dumpsys activity activities 2>/dev/null \
        | grep -A 30 "$PKG" > "$dump_dir/activity.txt" 2>/dev/null || true

    # Window state
    adb shell dumpsys window windows 2>/dev/null \
        | grep -A 20 "$PKG" > "$dump_dir/window.txt" 2>/dev/null || true

    # Frame stats
    adb shell dumpsys gfxinfo "$PKG" 2>/dev/null \
        > "$dump_dir/gfxinfo.txt" 2>/dev/null || true

    # Thread traces
    local pid
    pid=$(get_pid)
    if [ -n "$pid" ]; then
        adb shell run-as "$PKG" kill -3 "$pid" 2>/dev/null || true
        sleep 2
        adb logcat -d -t 200 --pid="$pid" > "$dump_dir/logcat-recent.txt" 2>/dev/null || true
    fi

    log "  Diagnostics saved."
}

# ── Main ────────────────────────────────────────────────────────────

adb wait-for-device
adb logcat -c

echo "=== EbookNT debug monitor ==="
echo "Package  : $PKG"
echo "Logcat   : $RAW_LOG"
echo "Events   : $EVENT_LOG"
echo "Screen   : poll every ${POLL_INTERVAL}s (foreground only)"
if command -v python3 &>/dev/null; then
    echo "Detection: python3 pixel analysis"
else
    echo "Detection: PNG size heuristic (<${BLACK_THRESHOLD_KB}KB)"
fi
echo ""
echo "Monitoring for crashes + black screens... (Ctrl+C to stop)"
echo ""

# ── Background: logcat → crash detection ────────────────────────────

adb logcat \
    AndroidRuntime:E \
    DEBUG:E \
    libc:F \
    CrashLogger:V \
    EbookNT:W \
    ActivityManager:W \
    system_server:E \
    '*:F' \
    '*:S' \
    -v threadtime \
    > "$RAW_LOG" &
BG_PIDS+=($!)

tail -f "$RAW_LOG" 2>/dev/null | while IFS= read -r line; do
    case "$line" in
        *"FATAL EXCEPTION"*|*"FATAL SIGNAL"*|*"Native crash"*|*"ANR in"*|*"signal"*[0-9]*"(SI"*)
            log "CRASH: $line"
            dump_diagnostics "crash"
            ;;
    esac
done &
BG_PIDS+=($!)

# ── Foreground: periodic black-screen polling ───────────────────────

consecutive_black=0

while true; do
    sleep "$POLL_INTERVAL"

    if ! is_foreground; then
        consecutive_black=0
        continue
    fi

    adb exec-out screencap -p > "$TMPSHOT" 2>/dev/null || continue

    if is_black_screen "$TMPSHOT"; then
        consecutive_black=$((consecutive_black + 1))
        if [ "$consecutive_black" -eq 2 ]; then
            log "BLACK SCREEN detected (confirmed over ${POLL_INTERVAL}s x2)"
            dump_diagnostics "black"
        elif [ "$consecutive_black" -gt 2 ]; then
            log "BLACK SCREEN persists (${consecutive_black} consecutive polls)"
        fi
    else
        if [ "$consecutive_black" -ge 2 ]; then
            log "Black screen recovered after ${consecutive_black} polls."
        fi
        consecutive_black=0
    fi
done

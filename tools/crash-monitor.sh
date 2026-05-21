#!/bin/bash
#
# EbookNT crash monitor — run this with adb connected, use the app normally.
# Only crash-relevant logcat lines are captured. Ctrl+C to stop.
#
# Usage:
#   ./tools/crash-monitor.sh              # default output dir ~/ebooknt-logs
#   ./tools/crash-monitor.sh /tmp/logs    # custom output dir

LOG_DIR="${1:-$HOME/ebooknt-logs}"
mkdir -p "$LOG_DIR"

STAMP=$(date +%Y%m%d-%H%M%S)
RAW_LOG="$LOG_DIR/logcat-$STAMP.log"
CRASH_LOG="$LOG_DIR/crashes-$STAMP.log"

adb wait-for-device

# Get app PID (may not be running yet)
refresh_pid() {
    APP_PID=$(adb shell pidof org.ebooknt.viewer 2>/dev/null | tr -d '\r')
}

echo "=== EbookNT crash monitor ==="
echo "Raw log  : $RAW_LOG"
echo "Crash log: $CRASH_LOG"
echo ""

# Clear old logcat buffer so we start fresh
adb logcat -c

# Capture filtered logcat to file.
# Filters (low volume during normal operation):
#   AndroidRuntime:E   — Java crash stack traces
#   DEBUG:E            — native crash / tombstone
#   libc:F             — native abort()
#   CrashLogger:*      — our crash logger output
#   EbookNT:W          — app warnings and errors
#   ActivityManager:W  — process crash/ANR notices from system
#   system_server:E    — system-level errors
#   *:F                — anything fatal from any source
#   *:S                — silence everything else
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
LOGCAT_PID=$!

echo "logcat PID: $LOGCAT_PID (adb logcat running in background)"
echo "Monitoring for crashes... (Ctrl+C to stop)"
echo ""

# Tail the raw log, grep for crash signatures, append to crash log.
# This gives a quick-glance summary without reading the full raw log.
tail -f "$RAW_LOG" 2>/dev/null | while IFS= read -r line; do
    case "$line" in
        *"FATAL EXCEPTION"*|*"FATAL SIGNAL"*|*"Native crash"*|*"ANR in"*|*"signal"*[0-9]*"(SI"*)
            echo "$line"
            echo "$line" >> "$CRASH_LOG"
            echo ">>> CRASH DETECTED at $(date '+%H:%M:%S') — see $RAW_LOG for full trace" | tee -a "$CRASH_LOG"
            echo ""
            ;;
    esac
done &
TAIL_PID=$!

cleanup() {
    kill "$LOGCAT_PID" "$TAIL_PID" 2>/dev/null
    wait "$LOGCAT_PID" "$TAIL_PID" 2>/dev/null

    RAW_SIZE=$(wc -c < "$RAW_LOG" 2>/dev/null || echo 0)
    echo ""
    echo "=== Session ended ==="
    echo "Raw log  : $RAW_LOG ($((RAW_SIZE / 1024)) KB)"
    if [ -s "$CRASH_LOG" ]; then
        echo "Crash log: $CRASH_LOG (crashes found!)"
    else
        echo "No crashes detected during this session."
        rm -f "$CRASH_LOG"
    fi
}
trap cleanup EXIT INT TERM

wait "$LOGCAT_PID"

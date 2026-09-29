#!/usr/bin/env bash
# adb helpers for the R3 pre-launch pass (docs/release/qa.md). Each subcommand does the part of a
# check that a machine can do; the rest (looking at the screen, listening to TTS) is in the runbook.
#
#   scripts/qa/device-checks.sh info                    device, OS, refresh rate: fill the results log
#   scripts/qa/device-checks.sh install <apk|apks>      install the release build (R8), keeping data
#   scripts/qa/device-checks.sh startup                 cold-start time (5 runs, TotalTime in ms)
#   scripts/qa/device-checks.sh frames reset            zero the frame counters
#   scripts/qa/device-checks.sh frames report <label>   janky-frame share and percentiles since reset
#   scripts/qa/device-checks.sh airplane on|off         toggle airplane mode (Android 11+)
#   scripts/qa/device-checks.sh push-apkg [file.apkg]   copy an Anki package to Downloads
#   scripts/qa/device-checks.sh mock-ai                 serve the canned OpenAI-compatible replies
#   scripts/qa/device-checks.sh permissions             what the app holds and what it may still ask for
#
# Set ANDROID_SERIAL when more than one device is attached.
set -euo pipefail

PKG=com.yahyafati.mnemo
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

need_adb() {
  command -v adb >/dev/null || { echo "adb not found: install the Android SDK platform-tools." >&2; exit 1; }
  [ "$(adb get-state 2>/dev/null)" = "device" ] || { echo "No device: connect one and allow USB debugging." >&2; exit 1; }
}

sdk() { adb shell getprop ro.build.version.sdk | tr -d '\r'; }

case "${1:-}" in
  info)
    need_adb
    echo "Device:        $(adb shell getprop ro.product.manufacturer | tr -d '\r') $(adb shell getprop ro.product.model | tr -d '\r')"
    echo "Android:       $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(sdk))"
    echo "One UI/skin:   $(adb shell getprop ro.build.version.oneui | tr -d '\r')"
    echo "Refresh rates: $(adb shell dumpsys display | grep -o 'fps=[0-9.]*' | sort -u | tr '\n' ' ')"
    echo "Screen:        $(adb shell wm size | tr -d '\r' | tail -1), $(adb shell wm density | tr -d '\r' | tail -1)"
    echo "Font scale:    $(adb shell settings get system font_scale | tr -d '\r')"
    if adb shell pm list packages "$PKG" | grep -q "$PKG"; then
      echo "Mnemo:         $(adb shell dumpsys package "$PKG" | grep -m1 versionName | tr -d ' \r') (versionCode $(adb shell dumpsys package "$PKG" | grep -m1 -o 'versionCode=[0-9]*' | cut -d= -f2))"
    else
      echo "Mnemo:         not installed"
    fi
    ;;

  install)
    need_adb
    file="${2:?usage: install <app-release.apk | mnemo.apks>}"
    case "$file" in
      *.apks)
        command -v bundletool >/dev/null || { echo "bundletool not found." >&2; exit 1; }
        bundletool install-apks --apks="$file" ;;
      *.apk)
        # -r keeps the data: this is the upgrade check. The APK must be signed with the same key as the installed build.
        adb install -r "$file" ;;
      *) echo "Expected .apk or .apks" >&2; exit 1 ;;
    esac
    ;;

  startup)
    need_adb
    for i in 1 2 3 4 5; do
      adb shell am force-stop "$PKG"
      sleep 1
      adb shell am start -W -n "$PKG/.MainActivity" | grep -E "TotalTime" | tr -d '\r' | sed "s/^/run $i: /"
    done
    ;;

  frames)
    need_adb
    case "${2:-}" in
      reset)
        adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
        echo "Counters reset. Do the scenario now (for example, 60 s of study), then: frames report <label>." ;;
      report)
        label="${3:-scenario}"
        out="$(adb shell dumpsys gfxinfo "$PKG")"
        echo "== $label =="
        echo "$out" | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number Slow UI thread|Number Slow issue draw commands|Number Frame deadline missed"
        echo "(Compare the 90th/95th percentiles with the frame budget: 16 ms at 60 Hz, 8 ms at 120 Hz.)" ;;
      *) echo "usage: frames reset | frames report <label>" >&2; exit 1 ;;
    esac
    ;;

  airplane)
    need_adb
    case "${2:-}" in
      on)  adb shell cmd connectivity airplane-mode enable
           # Some OEM builds ignore the shell command for Wi-Fi; check and say so.
           sleep 3
           adb shell dumpsys wifi | grep -m1 "Wi-Fi is" || true
           echo "If Wi-Fi is still on, switch it off in the quick settings." ;;
      off) adb shell cmd connectivity airplane-mode disable ;;
      *)   echo "usage: airplane on|off" >&2; exit 1 ;;
    esac
    ;;

  push-apkg)
    need_adb
    file="${2:-$ROOT/core/anki/src/test/resources/modern.apkg}"
    adb push "$file" /sdcard/Download/ >/dev/null
    echo "Copied $(basename "$file") to Downloads. In Mnemo: Decks › Import Anki (.apkg)."
    echo "(The default is a small package written by real Anki. Use your own collection for the real check.)"
    ;;

  mock-ai)
    need_adb
    adb reverse tcp:11435 tcp:11435 >/dev/null
    echo "Add an Ollama-preset provider with base URL http://127.0.0.1:11435/v1 (marked local). Ctrl-C to stop."
    exec python3 "$ROOT/docs/release/assets/demo/mock_ai_server.py"
    ;;

  permissions)
    need_adb
    adb shell dumpsys package "$PKG" | sed -n '/requested permissions:/,/install permissions:/p'
    adb shell dumpsys package "$PKG" | sed -n '/runtime permissions:/,/^$/p'
    ;;

  *)
    sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac

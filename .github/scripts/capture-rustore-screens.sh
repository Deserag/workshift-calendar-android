#!/usr/bin/env bash
set -euo pipefail

mkdir -p rustore-screenshots
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell wm size 1080x2400
adb shell wm density 420
adb shell settings put system time_12_24 24 || true
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

# All screenshots are taken from an emulator using anonymous demo data.
# Android API 29 has no POST_NOTIFICATIONS runtime permission.
for screen in home month week schedules alarms settings types editDay; do
  echo "Capturing ${screen}"
  adb shell am force-stop com.example.workshift || true
  adb shell am start -W -n com.example.workshift/.StoreScreenshotActivity --es screen "$screen"
  sleep 3
  adb exec-out screencap -p > "rustore-screenshots/${screen}.png"
  test "$(stat -c%s "rustore-screenshots/${screen}.png")" -gt 10000
  file "rustore-screenshots/${screen}.png"
done

adb logcat -d -v brief | grep -E 'FATAL EXCEPTION|AndroidRuntime|Process: com.example.workshift' > rustore-screenshots/crash-scan.txt || true
if [ -s rustore-screenshots/crash-scan.txt ]; then
  echo "::error::Android crash reported during screenshot capture"
  cat rustore-screenshots/crash-scan.txt
  exit 1
fi

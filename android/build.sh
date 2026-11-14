#!/bin/sh
# Builds ShiftCalendar.apk with Ubuntu's aapt/dx/apksigner and android-33 android.jar.
# Needs: apt install aapt apksigner zipalign dalvik-exchange; android.jar from github.com/Sable/android-platforms (android-33).
set -e
: "${KS_PASS:?Set KS_PASS to the signing keystore password}"
cd "$(dirname "$0")"
JAR=${ANDROID_JAR:-/home/user/sdk/android.jar}
rm -rf gen obj bin && mkdir -p gen obj bin
aapt package -f -m -J gen -M AndroidManifest.xml -S res -I "$JAR" -F bin/res.apk
javac --release 8 -nowarn -cp "$JAR" -d obj gen/com/shiftcal/widget/R.java src/com/shiftcal/widget/*.java
dalvik-exchange --dex --min-sdk-version=26 --output=bin/classes.dex obj
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$JAR" -F bin/unsigned.apk
(cd bin && aapt add unsigned.apk classes.dex >/dev/null && zipalign -f -p 4 unsigned.apk aligned.apk)
apksigner sign --ks "${KEYSTORE:-release.keystore}" --ks-pass env:KS_PASS --key-pass env:KS_PASS --out bin/ShiftCalendar.apk bin/aligned.apk
apksigner verify bin/ShiftCalendar.apk && echo OK

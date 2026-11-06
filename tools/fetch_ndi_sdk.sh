#!/bin/sh
# Fetch the official NDI SDK for Android and unpack the parts the build needs into app/ndi-sdk/
# (git-ignored: the NDI library is never committed). Running this means accepting the NDI SDK
# License Agreement — it is printed into app/ndi-sdk/ alongside the files.
# The installer script is NOT executed: its payload is cut out with awk/tail and untarred.
set -eu
URL="https://downloads.ndi.tv/SDK/NDI_SDK_Android/Install_NDI_SDK_v6_Android.tar.gz"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
DEST="$ROOT/app/ndi-sdk"
if [ -f "$DEST/lib/arm64-v8a/libndi.so" ] && [ -f "$DEST/include/Processing.NDI.Lib.h" ]; then
  echo "NDI SDK already present in $DEST"; exit 0
fi
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
echo "Downloading $URL"
curl -fsSL --retry 4 -o "$TMP/sdk.tar.gz" "$URL"
tar -xzf "$TMP/sdk.tar.gz" -C "$TMP"
SH=$(ls "$TMP"/Install_NDI_SDK_*_Android.sh | head -1)
LINE=$(awk '/^__NDI_ARCHIVE_BEGIN__/ { print NR+1; exit 0; }' "$SH")
mkdir -p "$TMP/x"
tail -n +"$LINE" "$SH" | tar -xz -C "$TMP/x"
SDK="$TMP/x/NDI SDK for Android"
rm -rf "$DEST"; mkdir -p "$DEST/lib/arm64-v8a"
cp -R "$SDK/include" "$DEST/include"
cp "$SDK/lib/arm64-v8a/libndi.so" "$DEST/lib/arm64-v8a/"
cp "$SDK/Version.txt" "$SDK/NDI SDK License Agreement.txt" "$DEST/"
cp -R "$SDK/licenses" "$DEST/licenses"
echo "NDI SDK $(cat "$DEST/Version.txt") -> $DEST"

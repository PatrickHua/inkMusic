#!/usr/bin/env bash
# Copy the Mac's inkMusic staging folder onto the phone, then have the app rescan
# and fetch lyrics for anything new. Files already on the phone are kept; adb
# only adds or replaces what is in the staging folder.
#
#   tools/sync.sh [staging dir]        (default ~/Music/inkMusic-staging)
set -euo pipefail

STAGING="${1:-$HOME/Music/inkMusic-staging}"
AGENT="content://io.github.patrickhua.inkmusic.agent"

# The SD card when there is one, else internal storage.
VOLUME=$(adb shell 'for v in /storage/*-*; do [ -d "$v" ] && echo "$v" && exit; done; echo /storage/emulated/0' | tr -d '\r')
ROOT="$VOLUME/Music/inkMusic"
echo "Phone library: $ROOT"

for dir in songs playlists; do
  if [ -d "$STAGING/$dir" ]; then
    adb shell mkdir -p "$ROOT/$dir"
    adb push --sync "$STAGING/$dir/." "$ROOT/$dir/" | tail -1
  fi
done

adb shell content call --uri "$AGENT" --method RESCAN
adb shell content call --uri "$AGENT" --method FETCH_LYRICS

"""Plan an artist's full discography: every album and single on YouTube Music,
minus live albums, compilations, remix/karaoke releases, and songs the
library already has. Writes a plan for fetch.py.

    python plan_discography.py library.json plan.json --min-songs 15
    python plan_discography.py library.json plan.json --artist "Taylor Swift" --artist 周杰伦
"""
import argparse
import collections
import json
import re
import sys

from ytmusicapi import YTMusic

from plan_artists import is_variant, norm

SKIP_RELEASE = re.compile(
    r"\b(live|concert|tour|greatest hits|best of|the best|collection|essential|anthology|"
    r"remix|karaoke|instrumental|acoustic|demo|精選|精选|演唱會|演唱会|現場|现场|伴奏|紀念|纪念)\b",
    re.IGNORECASE,
)


def releases(yt, artist_id):
    """Albums then singles, newest first, as (browseId, title, year)."""
    info = yt.get_artist(artist_id)
    found = []
    for kind in ("albums", "singles"):
        section = info.get(kind) or {}
        items = section.get("results", [])
        if section.get("browseId") and section.get("params"):
            try:
                items = yt.get_artist_albums(section["browseId"], section["params"], limit=None)
            except Exception:
                pass
        found += [(a["browseId"], a["title"], a.get("year")) for a in items if a.get("browseId")]
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("library")
    ap.add_argument("out")
    ap.add_argument("--min-songs", type=int, default=15)
    ap.add_argument("--artist", action="append", default=[], help="library artist name (album artist)")
    args = ap.parse_args()

    songs = json.load(open(args.library))["songs"]
    have = {norm(s["title"]) for s in songs}
    by_folder = collections.defaultdict(list)
    for s in songs:
        if (s.get("path") or "").startswith("songs/Songs/"):
            by_folder[s["path"].split("/")[2]].append(s)

    wanted = []
    for folder, items in sorted(by_folder.items(), key=lambda kv: -len(kv[1])):
        display = collections.Counter(s.get("albumArtist") or s["artist"] for s in items).most_common(1)[0][0]
        if (args.artist and (display in args.artist or folder in args.artist)) or (
            not args.artist and len(items) >= args.min_songs
        ):
            wanted.append((folder, display))

    yt = YTMusic()
    plan = []
    for folder, display in wanted:
        hit = yt.search(display, filter="artists", limit=1)
        if not hit:
            print(f"!! {display}: not found", file=sys.stderr)
            continue
        artist_id = hit[0]["browseId"]
        added, kept_releases = 0, 0
        for browse_id, title, year in releases(yt, artist_id):
            if SKIP_RELEASE.search(title):
                continue
            try:
                album = yt.get_album(browse_id)
            except Exception:
                continue
            kept_releases += 1
            for t in album.get("tracks", []):
                key = norm(t.get("title"))
                credited = any(a.get("id") == artist_id for a in t.get("artists") or [])
                if not t.get("videoId") or not key or key in have or is_variant(t.get("title")) or not credited:
                    continue
                have.add(key)
                plan.append({
                    "videoId": t["videoId"],
                    "title": t["title"],
                    "artist": display,
                    "artists": [a["name"] for a in t.get("artists") or []],
                    "album": album.get("title") or title,
                    "dir": f"songs/Songs/{folder}",
                })
                added += 1
        print(f"{display}: {kept_releases} releases, +{added} new songs", file=sys.stderr)

    json.dump(plan, open(args.out, "w"), ensure_ascii=False, indent=1)
    print(f"{len(plan)} tracks planned", file=sys.stderr)


if __name__ == "__main__":
    main()

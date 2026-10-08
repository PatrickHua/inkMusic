"""Plan downloads: each main artist's most popular songs that the library lacks.

Reads inkMusic's library.json, ranks each artist's songs by Deezer's public
top-tracks list (by plays), finds each on YouTube Music, and writes a plan
(JSON list of tracks) for fetch.py. Nothing is downloaded here.

    python plan_artists.py library.json plan.json --min-songs 3 --per-artist 10
"""
import argparse
import collections
import json
import re
import sys
import urllib.parse
import urllib.request

from opencc import OpenCC
from ytmusicapi import YTMusic

to_simplified = OpenCC("t2s")


VARIANT = re.compile(
    r"\b(live|remix|mix|demo|karaoke|instrumental|acoustic|edit|sped up|slowed|version|cover|伴奏|現場|现场)\b",
    re.IGNORECASE,
)


def norm(text):
    """Comparable form of a title: simplified Chinese, no brackets or subtitles, letters/digits only."""
    text = to_simplified.convert(text or "").lower()
    for pattern in (r"《[^》]*》", r"\([^)]*\)", r"（[^）]*）", r"\[[^\]]*\]", r"【[^】]*】"):
        text = re.sub(pattern, "", text)
    text = text.split(" - ")[0]
    text = re.sub(r"\b(feat|ft)\..*$", "", text)
    return re.sub(r"[\W_]+", "", text)


def is_variant(title):
    """Live takes, remixes, demos and the like; "Remastered" originals are fine."""
    return bool(VARIANT.search(title or ""))


def deezer(path, **params):
    url = f"https://api.deezer.com/{path}?" + urllib.parse.urlencode(params)
    with urllib.request.urlopen(url, timeout=20) as response:
        return json.load(response)


def deezer_top(name, limit=60):
    """The artist's tracks ranked by plays, as (title, duration seconds)."""
    hits = deezer("search/artist", q=name).get("data", [])
    if not hits:
        return []
    # Many artists share a name ("Adele"); the real one has by far the most fans.
    target = norm(name)
    same_name = [a for a in hits if norm(a["name"]) == target] or hits
    artist = max(same_name, key=lambda a: a.get("nb_fan", 0))
    return [(t["title"], t.get("duration")) for t in deezer(f"artist/{artist['id']}/top", limit=limit).get("data", [])]


def find_on_youtube(yt, title, artist_id, artist_name, seconds):
    """Best YouTube Music match for a Deezer track: same artist, similar length."""
    for t in yt.search(f"{title} {artist_name}", filter="songs", limit=8):
        credited = any(a.get("id") == artist_id for a in t.get("artists") or [])
        close = seconds is None or abs((t.get("duration_seconds") or 0) - seconds) <= 6
        if credited and close and norm(t.get("title")) == norm(title) and not is_variant(t.get("title")):
            return t
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("library")
    ap.add_argument("out")
    ap.add_argument("--min-songs", type=int, default=3)
    ap.add_argument("--per-artist", type=int, default=10)
    args = ap.parse_args()

    songs = json.load(open(args.library))["songs"]
    local = [s for s in songs if (s.get("path") or "").startswith("songs/Songs/")]
    by_folder = collections.defaultdict(list)
    for s in local:
        by_folder[s["path"].split("/")[2]].append(s)

    library_titles = {norm(s["title"]) for s in songs}
    yt = YTMusic()
    plan = []
    for folder, items in sorted(by_folder.items(), key=lambda kv: -len(kv[1])):
        if len(items) < args.min_songs:
            continue
        display = collections.Counter(s.get("albumArtist") or s["artist"] for s in items).most_common(1)[0][0]
        # Compare against the whole library: songs may sit under another artist's folder.
        have = set(library_titles)
        found = yt.search(display, filter="artists", limit=3)
        if not found:
            print(f"!! no artist match for {display}", file=sys.stderr)
            continue
        artist = found[0]
        ranked = deezer_top(artist["artist"]) or deezer_top(display)
        picked = []
        for title, seconds in ranked:
            key = norm(title)
            if not key or key in have or is_variant(title):
                continue
            track = find_on_youtube(yt, title, artist["browseId"], artist["artist"], seconds)
            if track is None:
                continue
            have.add(key)
            picked.append(track)
            if len(picked) >= args.per_artist:
                break
        if len(picked) < args.per_artist:
            # Deezer knows few Chinese artists; top up from the YouTube Music artist page,
            # whose top songs are ranked by popularity, then the full songs list.
            info = yt.get_artist(artist["browseId"])
            section = info.get("songs", {})
            fill = list(section.get("results", []))
            if section.get("browseId"):
                try:
                    fill += yt.get_playlist(section["browseId"], limit=60)["tracks"]
                except Exception:
                    pass
            for t in fill:
                key = norm(t.get("title"))
                if not t.get("videoId") or not key or key in have or is_variant(t.get("title")):
                    continue
                have.add(key)
                picked.append(t)
                if len(picked) >= args.per_artist:
                    break
        print(f"{display} [{artist['artist']}] has {len(items)}, +{len(picked)}: "
              + ", ".join(t["title"] for t in picked[:4]), file=sys.stderr)
        for t in picked:
            plan.append({
                "videoId": t["videoId"],
                "title": t["title"],
                "artist": display,
                "artists": [a["name"] for a in t.get("artists") or []],
                "album": (t.get("album") or {}).get("name"),
                "dir": f"songs/Songs/{folder}",
                "playlists": [],
            })
    json.dump(plan, open(args.out, "w"), ensure_ascii=False, indent=1)
    print(f"{len(plan)} tracks planned", file=sys.stderr)


if __name__ == "__main__":
    main()

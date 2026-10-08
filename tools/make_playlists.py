"""Write inkMusic playlists (.m3u8) from library.json: one per category folder,
plus 华语 (Chinese), J-Pop, English Pop, and Oldies built from the Songs folder.

    python make_playlists.py library.json out_dir/

Paths are relative to inkMusic/playlists/, so push out_dir's files there and
rescan. Playlists the app wrote (with an #INKMUSIC-PLAYLIST-ID) are left alone
because these files use their own names.
"""
import argparse
import json
import re
from pathlib import Path

CATEGORIES = {
    "Study": "Study",
    "Sleep": "Sleep",
    "ASMR": "ASMR",
    "Meditation": "Meditation",
    "Classical": "Classical",
    "Instrumental": "Piano & Instrumental",
}

# English-language artists whose music mostly predates 1990.
OLDIES = {
    "elvis presley", "the beatles", "sam cooke", "abba", "billy joel", "jim croce", "john denver",
    "frank sinatra", "roy orbison", "the righteous brothers", "carpenters", "dusty springfield",
    "the ronettes", "the monkees", "the jackson 5", "johnny cash", "cass elliot", "bob dylan",
    "van morrison", "don mclean", "cyndi lauper", "wham!", "journey", "blondie",
    "daryl hall & john oates", "bob seger", "édith piaf", "kris kristofferson", "willie nelson",
    "jay & the americans", "richard marx", "mc hammer", "john lennon", "david bowie", "queen",
    "michael jackson", "whitney houston", "the proclaimers", "aliotta haynes jeremiah", "nirvana",
    "4 non blondes", "concrete blonde", "kylie minogue", "ricchi e poveri", "power station",
}

KANA = re.compile(r"[぀-ヿ]")
HAN = re.compile(r"[一-鿿]")


def kind(song):
    text = f"{song.get('albumArtist') or ''} {song['artist']} {song['title']}"
    if KANA.search(text):
        return "J-Pop"
    if HAN.search(text):
        return "华语"
    artist = (song.get("albumArtist") or song["artist"]).split(",")[0].strip().lower()
    return "Oldies" if artist in OLDIES else "English Pop"


def write(path, name, songs):
    lines = ["#EXTM3U", f"#PLAYLIST:{name}"]
    for s in songs:
        seconds = (s.get("durationMs") or 0) // 1000 or -1
        label = " - ".join(x for x in (s.get("artist"), s["title"]) if x)
        lines += ["", f"#EXTINF:{seconds},{label}", f"#INKMUSIC-SONG-ID:{s['id']}", f"../{s['path']}"]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("library")
    ap.add_argument("out")
    args = ap.parse_args()

    songs = [s for s in json.load(open(args.library))["songs"] if s.get("path")]
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    groups = {}
    for s in songs:
        folder = s["path"].split("/")[1]
        if folder in CATEGORIES:
            groups.setdefault(CATEGORIES[folder], []).append(s)
        elif folder == "Songs":
            groups.setdefault(kind(s), []).append(s)

    for name, items in groups.items():
        items.sort(key=lambda s: s["path"].lower())
        write(out / f"{name}.m3u8", name, items)
        print(f"{name}: {len(items)}")


if __name__ == "__main__":
    main()

"""Plan downloads for the category folders (Study, Sleep, ASMR, Meditation,
Classical, Instrumental), matched to what each already holds.

Music categories come from YouTube Music; ambient, ASMR and guided meditation
come from YouTube videos capped at about an hour. Writes a plan for fetch.py.

    python plan_categories.py library.json plan.json --per-category 30
"""
import argparse
import json
import subprocess
import sys

from ytmusicapi import YTMusic

from plan_artists import is_variant, norm

# Study: instrumental ambient/electronic focus music, like the East Forest albums.
STUDY_ARTISTS = {
    "Brian Eno": 3, "Nils Frahm": 3, "Ólafur Arnalds": 3, "Tycho": 3, "Hania Rani": 2,
    "Bonobo": 2, "Max Richter": 2, "Jon Hopkins": 2, "Hiroshi Yoshimura": 2,
    "Helios": 2, "Goldmund": 2, "Emancipator": 2, "Explosions in the Sky": 2,
}

CLASSICAL = [
    "Dvořák Symphony No. 9 From the New World Largo", "Dvořák Symphony No. 9 Allegro con fuoco",
    "Beethoven Symphony No. 5 Allegro con brio", "Beethoven Symphony No. 7 Allegretto",
    "Beethoven Symphony No. 9 Ode to Joy finale", "Mozart Eine kleine Nachtmusik Allegro",
    "Mozart Symphony No. 40 Molto allegro", "Mozart Clarinet Concerto Adagio",
    "Tchaikovsky Swan Lake Scene", "Tchaikovsky Nutcracker Waltz of the Flowers",
    "Vivaldi Four Seasons Spring Allegro", "Vivaldi Four Seasons Winter Allegro non molto",
    "Vivaldi Four Seasons Summer Presto", "Bach Brandenburg Concerto No. 3 Allegro",
    "Handel Water Music Alla Hornpipe", "Elgar Enigma Variations Nimrod",
    "Barber Adagio for Strings", "Holst The Planets Jupiter",
    "Mahler Symphony No. 5 Adagietto", "Smetana Má vlast Vltava Moldau",
    "Brahms Hungarian Dance No. 5", "Rachmaninoff Piano Concerto No. 2 Moderato",
    "Saint-Saëns The Swan Carnival of the Animals", "Sibelius Finlandia",
    "Mendelssohn Violin Concerto E minor Allegro molto appassionato",
    "Schubert Symphony No. 8 Unfinished Allegro moderato", "Ravel Boléro",
    "Johann Strauss II The Blue Danube", "Rimsky-Korsakov Scheherazade The Young Prince and the Young Princess",
    "Borodin Polovtsian Dances", "Haydn Symphony No. 94 Surprise Andante", "Grieg Peer Gynt Morning Mood",
]

PIANO = [
    "Satie Gnossienne No. 1", "Satie Gymnopédie No. 3", "Debussy Rêverie", "Debussy La fille aux cheveux de lin",
    "Debussy Golliwogg's Cakewalk", "Liszt Liebestraum No. 3", "Liszt La Campanella",
    "Rachmaninoff Prelude in C-sharp minor Op. 3 No. 2", "Beethoven Moonlight Sonata Adagio sostenuto",
    "Beethoven Moonlight Sonata Presto agitato", "Beethoven Für Elise", "Beethoven Pathétique Adagio cantabile",
    "Mozart Piano Sonata No. 16 K. 545 Allegro", "Mozart Rondo alla Turca", "Schumann Träumerei",
    "Bach Prelude in C major BWV 846", "Schubert Impromptu Op. 90 No. 3", "Ludovico Einaudi I Giorni",
    "Ludovico Einaudi Una Mattina", "Yiruma Kiss the Rain", "Joe Hisaishi Summer piano",
    "Joe Hisaishi Merry-Go-Round of Life piano", "Ryuichi Sakamoto Energy Flow", "Philip Glass Opening Glassworks",
    "Mendelssohn Songs without Words Venetian Gondola Song", "Tchaikovsky The Seasons June Barcarolle",
    "Ravel Pavane pour une infante défunte piano", "Brahms Intermezzo Op. 118 No. 2", "Mozart Fantasia in D minor K. 397",
    "Grieg Lyric Pieces Arietta", "Debussy Arabesque No. 2",
]

# (query, how many) for YouTube video searches.
SLEEP = [
    ("rain sounds for sleeping 1 hour", 3), ("gentle rain on tent 1 hour", 2), ("ocean waves for sleep 1 hour", 3),
    ("forest night crickets ambience 1 hour", 2), ("thunderstorm sounds for sleeping 1 hour", 2),
    ("brown noise 1 hour", 2), ("pink noise for sleep 1 hour", 1), ("fireplace crackling sounds 1 hour", 2),
    ("rain on window sounds 1 hour", 2), ("river stream sounds 1 hour", 2), ("wind sounds for sleep 1 hour", 1),
    ("snowstorm ambience 1 hour", 1), ("rain on roof sounds 1 hour", 2), ("waterfall sounds 1 hour", 2),
    ("summer night ambience 1 hour", 1), ("airplane cabin white noise 1 hour", 1), ("lake waves sounds 1 hour", 1),
]
MEDITATION = [
    ("Swami Sarvapriyananda guided meditation", 4), ("Ramakrishna Mission guided meditation", 3),
    ("Tara Brach guided meditation", 3), ("Jack Kornfield guided meditation", 3),
    ("Thich Nhat Hanh guided meditation", 3), ("body scan meditation 30 minutes", 3),
    ("yoga nidra guided 30 minutes", 3), ("loving kindness meditation guided", 3),
    ("Jon Kabat-Zinn guided meditation", 3), ("Sam Harris guided meditation", 2),
]
ASMR_CHANNEL = "https://www.youtube.com/channel/UClMJgjg2z_IrRm6J9KrhcuQ/videos"


def video_search(query, count, have, min_s, max_s):
    """First `count` YouTube videos for `query` within the length range."""
    out = subprocess.run(
        ["yt-dlp", "--flat-playlist", "--print", "%(id)s\t%(duration)s\t%(channel)s\t%(title)s", f"ytsearch{count * 6}:{query}"],
        capture_output=True, text=True,
    ).stdout
    return pick_videos(out, count, have, min_s, max_s)


def pick_videos(listing, count, have, min_s, max_s):
    picked = []
    for line in listing.splitlines():
        parts = line.split("\t")
        if len(parts) != 4:
            continue
        vid, duration, channel, title = parts
        try:
            seconds = float(duration)
        except ValueError:
            continue
        if not (min_s <= seconds <= max_s) or norm(title) in have:
            continue
        have.add(norm(title))
        picked.append({"videoId": vid, "title": title, "artist": channel})
        if len(picked) >= count:
            break
    return picked


def song_search(yt, query, have):
    for t in yt.search(query, filter="songs", limit=5):
        if t.get("videoId") and norm(t["title"]) not in have and not is_variant(t["title"]):
            have.add(norm(t["title"]))
            return t
    return None


def entry(track, directory, artist=None):
    names = [a["name"] for a in track.get("artists") or []]
    return {
        "videoId": track["videoId"],
        "title": track["title"],
        "artist": artist or (names[0] if names else track.get("artist", "")),
        "artists": names,
        "album": (track.get("album") or {}).get("name"),
        "dir": directory,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("library")
    ap.add_argument("out")
    ap.add_argument("--per-category", type=int, default=30)
    args = ap.parse_args()
    n = args.per_category

    have = {norm(s["title"]) for s in json.load(open(args.library))["songs"]}
    yt = YTMusic()
    plan = []

    for artist, count in STUDY_ARTISTS.items():
        found = yt.search(artist, filter="artists", limit=1)
        if not found:
            continue
        tracks = yt.get_artist(found[0]["browseId"]).get("songs", {}).get("results", [])
        taken = 0
        for t in tracks:
            if taken >= count or norm(t["title"]) in have or is_variant(t["title"]):
                continue
            have.add(norm(t["title"]))
            plan.append(entry(t, f"songs/Study/{artist}", artist))
            taken += 1
    print(f"Study: {sum(1 for p in plan if '/Study/' in p['dir'])}", file=sys.stderr)

    for name, queries in (("Classical", CLASSICAL), ("Instrumental", PIANO)):
        sub = "Masterworks" if name == "Classical" else "Piano"
        before = len(plan)
        for q in queries:
            if len(plan) - before >= n:
                break
            t = song_search(yt, q, have)
            if t:
                plan.append(entry(t, f"songs/{name}/{sub}"))
        print(f"{name}: {len(plan) - before}", file=sys.stderr)

    for name, queries, sub, lo, hi in (
        ("Sleep", SLEEP, "Ambience", 15 * 60, 65 * 60),
        ("Meditation", MEDITATION, "Guided", 8 * 60, 65 * 60),
    ):
        before = len(plan)
        for q, count in queries:
            for v in video_search(q, count, have, lo, hi):
                plan.append({**v, "artists": [v["artist"]], "album": None, "dir": f"songs/{name}/{sub}"})
        print(f"{name}: {len(plan) - before}", file=sys.stderr)

    listing = subprocess.run(
        ["yt-dlp", "--flat-playlist", "--playlist-end", "150", "--print",
         "%(id)s\t%(duration)s\tGoodnight Moon\t%(title)s", ASMR_CHANNEL],
        capture_output=True, text=True,
    ).stdout
    asmr = pick_videos(listing, n, have, 15 * 60, 65 * 60)
    plan += [{**v, "artists": [v["artist"]], "album": None, "dir": "songs/ASMR/Goodnight Moon"} for v in asmr]
    print(f"ASMR: {len(asmr)}", file=sys.stderr)

    json.dump(plan, open(args.out, "w"), ensure_ascii=False, indent=1)
    print(f"{len(plan)} tracks planned", file=sys.stderr)


if __name__ == "__main__":
    main()

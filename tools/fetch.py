"""Download a plan (from plan_artists.py / plan_categories.py) into a staging copy
of the inkMusic folder: m4a audio from YouTube, tagged with title, artist, album
artist, and album. Push the staging folder to the phone with sync.sh.

    python fetch.py plan.json ~/Music/inkMusic-staging --workers 4

Already-downloaded tracks are skipped, so an interrupted run can be resumed.
"""
import argparse
import concurrent.futures
import json
import re
import subprocess
import sys
import time
from pathlib import Path

from mutagen.mp4 import MP4


def safe(name):
    return re.sub(r'[\\/:*?"<>|]', "_", name).strip(" .")[:120] or "Untitled"


def target_for(root, item):
    title = safe(item["title"])
    stem = f"{safe(item['artist'])} - {title}" if item.get("artist") else title
    return Path(root) / item["dir"] / f"{stem}.m4a"


def fetch(root, item):
    target = target_for(root, item)
    if target.exists():
        return "skip", item
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_suffix(".part.m4a")
    # YouTube intermittently answers 403; a retry after a pause usually works.
    for attempt in range(3):
        result = subprocess.run(
            [
                "yt-dlp", "--quiet", "--no-warnings", "--no-playlist",
                # m4a (AAC) plays everywhere and needs no re-encoding.
                "-f", "bestaudio[ext=m4a]/bestaudio",
                "-o", str(partial),
                f"https://www.youtube.com/watch?v={item['videoId']}",
            ],
            capture_output=True, text=True,
        )
        if result.returncode == 0 and partial.exists():
            break
        partial.unlink(missing_ok=True)
        time.sleep(5 * (attempt + 1))
    if result.returncode != 0 or not partial.exists():
        partial.unlink(missing_ok=True)
        return "fail", {**item, "error": result.stderr.strip()[-300:]}

    audio = MP4(partial)
    audio["\xa9nam"] = item["title"]
    audio["\xa9ART"] = ", ".join(item.get("artists") or [item.get("artist", "")])
    if item.get("artist"):
        audio["aART"] = item["artist"]
    if item.get("album"):
        audio["\xa9alb"] = item["album"]
    audio.save()
    partial.rename(target)
    return "ok", item


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("plan")
    ap.add_argument("root")
    ap.add_argument("--workers", type=int, default=4)
    args = ap.parse_args()

    plan = json.load(open(args.plan))
    counts = {"ok": 0, "skip": 0, "fail": 0}
    failures = []
    with concurrent.futures.ThreadPoolExecutor(args.workers) as pool:
        for i, (status, item) in enumerate(pool.map(lambda it: fetch(args.root, it), plan), 1):
            counts[status] += 1
            if status == "fail":
                failures.append(item)
            if i % 20 == 0 or i == len(plan):
                print(f"{i}/{len(plan)} {counts}", file=sys.stderr, flush=True)
    if failures:
        Path(args.plan).with_suffix(".failed.json").write_text(json.dumps(failures, ensure_ascii=False, indent=1))
        print(f"{len(failures)} failed; see {Path(args.plan).with_suffix('.failed.json')}", file=sys.stderr)


if __name__ == "__main__":
    main()

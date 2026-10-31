#!/usr/bin/env python3
"""Merge BetterModel's generated resource pack into this repo's resourcepack/ folder.

BetterModel turns the .bbmodel files in plugins/BetterModel/models/ into a resource pack of its own:
the folder or zip named by `build-folder-location` in plugins/BetterModel/config.yml (by default
`BetterModel/build` next to the server jar; some notes say plugins/BetterModel/build). Players need
those files too, so this script copies them into resourcepack/ here. Then commit and push: the GitHub
build publishes ONE combined ExplorerMaze-ResourcePack.zip and prints its new SHA-1 for config.yml.

Usage:
    python tools/merge_bettermodel_pack.py <path to BetterModel's build folder or build.zip>

Set this in plugins/BetterModel/config.yml first and restart the server once:
    pack-type: folder          # zip works too
    pack:
      use-obfuscation: false   # keeps the generated file names stable, so the merged pack keeps working
"""
import shutil
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEST = REPO / "resourcepack"
SKIP = {"pack.mcmeta", "pack.png"}


def copy_from_zip(src: Path):
    copied, replaced = 0, []
    with zipfile.ZipFile(src) as z:
        for info in z.infolist():
            if info.is_dir():
                continue
            rel = Path(*info.filename.split("/"))
            if rel.name in SKIP or rel.parts[0] != "assets":
                continue
            out = DEST / rel
            if out.exists():
                replaced.append(str(rel))
            out.parent.mkdir(parents=True, exist_ok=True)
            with z.open(info) as f, open(out, "wb") as g:
                shutil.copyfileobj(f, g)
            copied += 1
    return copied, replaced


def copy_from_folder(src: Path):
    assets = src / "assets"
    if not assets.is_dir():
        sys.exit(f"no 'assets' folder inside {src} - is this BetterModel's build folder?")
    copied, replaced = 0, []
    for p in assets.rglob("*"):
        if p.is_dir() or p.name in SKIP:
            continue
        rel = p.relative_to(src)
        out = DEST / rel
        if out.exists():
            replaced.append(str(rel))
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(p, out)
        copied += 1
    return copied, replaced


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    src = Path(sys.argv[1])
    if not src.exists():
        sys.exit(f"not found: {src}")
    if src.is_file() and src.suffix.lower() == ".zip":
        copied, replaced = copy_from_zip(src)
    else:
        copied, replaced = copy_from_folder(src)
    print(f"copied {copied} file(s) into {DEST}")
    if replaced:
        print("replaced existing files:")
        for r in replaced:
            print("  " + r)
    print("Now commit and push. The GitHub build zips the merged pack and prints its new SHA-1.")


if __name__ == "__main__":
    main()

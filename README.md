# Explorer Maze (Paper 26.x)

`/arena` sends a player into a hidden void world. After a 60 s lobby countdown (even if alone) everyone
is dropped into a huge procedurally generated maze with long walls and endless halls. Escape through the
**dark oak door** before **the Explorer** drags you back. First to escape wins; everyone else loses and gets nothing.

## Contents
| Path | What |
|---|---|
| `src/`, `pom.xml` | The plugin (Java 25, Paper API 26.x) |
| `.github/workflows/build.yml` | GitHub Actions: builds jar, zips both packs, prints the SHA-1, publishes a release on `v*` tags |
| `resourcepack/` | Corrupted-withered-skulk bedrock + sculk textures, the Explorer skin, the HUD eye |
| `datapack/explorermaze_advancements/` | 15 advancements (plugin grants them) |
| `tools/generate_textures.py` | Regenerates every texture (`pip install pillow`) |

## Install
1. Push this repo to GitHub, tag a release (`git tag v1.0.0 && git push --tags`) - the workflow builds everything.
2. Put `ExplorerMaze.jar` in `plugins/`.
3. Put `ExplorerMaze-DataPack.zip` in `<main world>/datapacks/` and run `/reload` or restart (optional - advancements only).
4. Put the release URL of `ExplorerMaze-ResourcePack.zip` and the SHA-1 into `plugins/ExplorerMaze/config.yml`
   (`resource-pack.url` / `resource-pack.sha1`). Players are prompted when they run `/arena`.
5. Restart. The plugin creates the void world `explorer_arena`, builds the spawn arena and reward room, and
   generates a fresh maze at the start of every match.

## Rules implemented
- Inventory, XP, health, location are saved to disk on entry and restored on exit (crash-safe). Nothing from the maze is kept.
- Players are in Adventure mode, no natural regeneration, hunger frozen. Only potions heal (Regen Pot).
- Explorer: invulnerable, re-textured wither skeleton (scaled 1.35x).
  - near (28 blocks): heartbeat sounds + an eye appears on screen (font glyph from the resource pack)
  - sees you (24 blocks, line of sight): he speeds up, you get Darkness + Slowness
  - keeps you in sight for 3 s or touches you: 3 hearts of damage and you are teleported to one of the cells
    furthest from the exit
- Chests (mostly in dead ends): Regen Pot, Explorer Slower, Swiftness Draught, Shadow Cloak, Exit Whisper (arrow to exit),
  Echo Locator (distance + pitch), Explorer Tracker (he glows through walls), Breadcrumb Torches.
- The exit chimes softly and shows soul flames when you are within 45 blocks.
- The Explorer starts closer, hunts around random players (`arena.hunt-bias`) and sniffs out a player every 30 s.
- Exit: dark oak door in the outer wall -> black reward room with an enchanting table ->
  100 levels, 3 netherite ingots, plus independent chances for 2x Ancient City loot (35 %) and 1x End City loot (20 %).

## Commands
`/arena`, `/arena leave`, `/arena status`, `/arena pack` (re-sends the pack, shows if it is loaded); admins: `/arena forcestart | stop | regen | reload`.
Permissions: `explorermaze.play` (default true), `explorermaze.admin` (op).

## Notes
- Vanilla can't add new blocks via resource pack, so the "corrupted bedrock" re-textures **bedrock** and **sculk**,
  and the Explorer re-textures the **wither skeleton**. These textures apply everywhere while the pack is loaded.
- Sounds are vanilla (warden heartbeat / roar / sonic boom); no custom audio is bundled.
- Version strings: `paper.api.version` in `pom.xml` is a range that resolves to the newest Paper 26.x API.
  Pin it (e.g. `26.3.build.X-alpha`) for reproducible builds. `pack_format`/`min_format` in the two `pack.mcmeta`
  files are wide-range guesses - adjust if your client warns about the pack version.

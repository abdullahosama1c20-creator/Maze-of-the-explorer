# The Explorer's model (BetterModel)

`models/the_explorer.bbmodel` is the Explorer as a Blockbench model: his texture is inside the file,
and so are the animations `walk`, `run` and `sees_and_hits` (plus the original ones).
The plugin puts this model on top of the invisible wither skeleton that does the pathfinding and the
"sees you / catches you" logic, so nothing about the game rules changes.

## Server setup (once)
1. Install the free **BetterModel** plugin: https://modrinth.com/plugin/bettermodel
   (Paper 26.1 to 26.3, Java 25). Restart once so it creates `plugins/BetterModel/`.
2. Copy `models/the_explorer.bbmodel` into `plugins/BetterModel/models/`.
   Every GitHub release of this repo also has the `.bbmodel` attached.
3. In `plugins/BetterModel/config.yml` set
   ```yaml
   pack-type: folder
   pack:
     use-obfuscation: false
   ```
   (`folder` makes the generated files easy to copy; obfuscation off keeps their names stable.)
4. Run `/bettermodel reload`, then `/arena model`: it lists the loaded model and which animations it found.
5. Quick look without a match: `/bettermodel spawn the_explorer` and `/bm test the_explorer run`.

## Players need the model's resource pack too
BetterModel writes a resource pack of its own to `BetterModel/build/` next to the server jar
(some builds use `plugins/BetterModel/build/`). Those files have to be in the pack players download:
- copy its `assets` folder into this repo's `resourcepack/` (on GitHub: open `resourcepack`,
  *Add file -> Upload files*, drag the `assets` folder in, commit), or run
  `python tools/merge_bettermodel_pack.py <path to the build folder or build.zip>` and push.
- The GitHub build then publishes one combined `ExplorerMaze-ResourcePack.zip`. Put its new SHA-1
  (shown in the release notes) into `plugins/ExplorerMaze/config.yml` and run `/arena reload`.

Repeat the copy whenever the model or its texture changes.

## Config (plugins/ExplorerMaze/config.yml)
```yaml
explorer:
  model:
    enabled: true
    name: 'the_explorer'
    scale: 1.0
    run-animation: 'run'
    sees-animation: 'sees_and_hits'
```
Without BetterModel, or when the model file is missing, the plugin logs a warning and uses the
re-textured wither skeleton instead.

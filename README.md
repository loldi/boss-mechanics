# Boss Mechanics

A RuneLite plugin that teaches you boss mechanics inside the game. Each supported
boss's collection log page gets a Boss Mechanics button that opens a native-styled
interface with:

- **Mechanics Discovered progress bar** — mechanics start as locked "???" rows and
  unlock when your client witnesses the boss use them mid-fight, with a
  collection-log-style chat message. Prefer spoilers? "View All" reveals everything
  (reversibly — your real discovery progress is kept).
- **Move list + counterplay** — what the boss does, and the one or two sentences
  that keep you alive.
- **Animated previews** — the boss model performing each move, rendered live from
  the game cache.

## Status

Pre-alpha. Boss data loads and validates at startup, and the detection engine
tracks which mechanics your client has witnessed, announcing each one in chat
the first time. Discovery (and reveal) state now persists per character via
RuneLite's profile config, replacing wholesale on character switch. Opening the
collection log on a supported boss now puts a Boss Mechanics button in the header
next to Combat Achievements, and clicking it opens the Boss Mechanics screen over
the log, shaped like the Combat Achievements boss screen: a "Mechanics Discovered"
progress bar across the top, then a scrollable list of moves on the left where
undiscovered ones read `???`, and the selected move's description and counterplay
on the right, dimmed until you have found it. A reversible "View All" sits above
the list, remembers itself per character, and a WIKI button in the title bar
opens the boss's wiki page. Selecting a mechanic plays its animation, looping, on
a 291x140 model box above its description, curated per mechanic to a zoom that
fits the animation's full frame range and a vertical anchor correction so the
model sits centred rather than riding high in the box; a `staticFallback`
mechanic shows the npc's own idle pose (never a raw T-pose) instead, and a
locked "???" row shows nothing. The window now wears the real steel Combat
Achievements chrome (frame, corners and title all pulled from the game's own
sprites), and a mechanic's preview can also be a bundled sprite image (for the
few moves whose colour the Widget API can't render live) or a second model
shown alongside the first. The window can be dragged by its title bar anywhere
on screen (never fully off it), and remembers where you put it across a "View
All" flip, a boss switch and a close/reopen for the rest of your session. It
also remembers its own position full stop: dragging or resizing the collection
log underneath no longer moves it, and an undragged window simply opens
covering wherever the log currently is. While you drag it, the window hides
to a plain grey outline that tracks the cursor, the same way the collection
log's own drag does, and reappears at the outline's position the moment you
let go. See
[docs/DECISIONS.md](docs/DECISIONS.md) for the locked design and
[data/SCHEMA.md](data/SCHEMA.md) for the boss data format.

**Launch bosses:** Abyssal Sire, Zulrah, Vorkath, General Graardor, Mad Angel.

## Development

Launch a RuneLite client with the plugin sideloaded:

```
gradlew runClient
```

(`BossMechanicsPluginTest` is a `main()` launcher, not a JUnit test, so
`gradlew test` will not run it. If you launch it from IntelliJ instead, add `-ea`
to the VM options — RuneLite refuses to start without assertions enabled.)

`gradlew build` runs the boss data validation tests against every file bundled
in `data/bosses/`. A data-only PR (adding or editing a boss JSON file) is green
when those tests pass — see [data/SCHEMA.md](data/SCHEMA.md) for the format and
how to add a boss.

## Data & attribution

Mechanic names, descriptions, and counterplay are hand-curated, researched from the
[Old School RuneScape Wiki](https://oldschool.runescape.wiki/) (CC BY-SA 3.0).
Every boss data file links its source page.

## License

BSD-2-Clause. See [LICENSE](LICENSE).

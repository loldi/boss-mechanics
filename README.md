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
next to Combat Achievements, and clicking it opens the Boss Mechanics window over
the log: a "Mechanics Discovered" progress bar, a scrollable list where
undiscovered moves read `???`, and a reversible "View All" that remembers itself
per character. The right-hand pane where the animated preview goes is still
empty; that's issue #6. See
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

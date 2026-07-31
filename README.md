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

Pre-alpha scaffold. Nothing works yet. See [docs/DECISIONS.md](docs/DECISIONS.md)
for the locked design and [data/SCHEMA.md](data/SCHEMA.md) for the boss data format.

**Launch bosses:** Abyssal Sire, Zulrah, Vorkath, General Graardor, Mad Angel.

## Development

Launch a RuneLite client with the plugin sideloaded:

```
gradlew runClient
```

(`BossMechanicsPluginTest` is a `main()` launcher, not a JUnit test, so
`gradlew test` will not run it. If you launch it from IntelliJ instead, add `-ea`
to the VM options — RuneLite refuses to start without assertions enabled.)

## Data & attribution

Mechanic names, descriptions, and counterplay are hand-curated, researched from the
[Old School RuneScape Wiki](https://oldschool.runescape.wiki/) (CC BY-SA 3.0).
Every boss data file links its source page.

## License

BSD-2-Clause. See [LICENSE](LICENSE).

# Boss Mechanics

A RuneLite plugin that teaches boss mechanics inside the game. Every supported
boss's collection log page gets a **Boss Mechanics** button. It opens a move list
built from the game's own interface parts: what the boss does, what you do about
it, and a looping preview of the boss actually doing it.

Mechanics start locked. They unlock as your client watches the boss use them.

<!-- PLACEHOLDER: hero gif. Collection log open on a supported boss -> click Boss
     Mechanics -> window opens -> click through 2-3 mechanics with previews playing. -->
![Boss Mechanics window](docs/images/hero.gif)

## What it does

### Discovery

Every mechanic is a `???` row until your client sees the boss use it in a real
fight. When it fires, the row fills in and the unlock is announced in chat, the
same way a collection log slot is. Progress shows in a **Mechanics Discovered**
bar across the top of the window.

Detection watches animations, projectiles, graphics and NPC spawns, and only
counts them while a boss NPC is present. Discovery is stored per character, so a
second account starts from scratch.

Not interested in the discovery loop? **View All** reveals everything. It is
reversible and remembers itself per character, so real discovery progress is
never lost.

<!-- PLACEHOLDER: gif. A ??? row unlocking mid-fight, chat message appearing,
     progress bar ticking up. -->
![Discovering a mechanic](docs/images/discovery.gif)

### Move list and counterplay

The left column lists the boss's mechanics in the order you meet them. The right
column holds the selected mechanic's description and its counterplay, kept to one
or two sentences. Undiscovered mechanics are dimmed. A **WIKI** button in the
title bar opens the boss's strategy page.

<!-- PLACEHOLDER: screenshot. Move list on the left, a selected mechanic's
     description + counterplay on the right. Mix of discovered and ??? rows. -->
![Move list and counterplay](docs/images/move-list.png)

### Animated previews

Selecting a mechanic plays the boss model performing that move, looping, rendered
live from the game cache. Each preview is curated per mechanic: zoom fitted to the
animation's full frame range, vertical anchoring corrected, and multi-stage moves
chained end to end (charge, hold, slam) rather than held on a single pose.

Mechanics with nothing to animate, such as projectile and ground-effect moves,
show the boss's idle pose or a bundled image instead.

<!-- PLACEHOLDER: gif. 3-4 previews in a row, ideally including a chained
     animation like Doom's Shockwave and a static/idle one. -->
![Animated previews](docs/images/previews.gif)

### The window

The window uses the game's real steel interface sprites and sits over the
collection log. Drag it by its title bar anywhere on screen. It hides to a grey
outline while dragging, the way the collection log does, and keeps its position
across a boss switch, a View All flip and a close/reopen.

<!-- PLACEHOLDER: gif. Dragging the window across the screen by its title bar,
     outline visible mid-drag, snapping back to full at drop. -->
![Dragging the window](docs/images/drag.gif)

## Supported bosses

| Boss | Mechanics |
|---|---|
| Abyssal Sire | 9 |
| Vorkath | 6 |
| Doom of Mokhaiotl | 11 |

## Settings

| Setting | Default | What it does |
|---|---|---|
| Discovery chat messages | On | Announce each newly discovered mechanic in the chatbox |
| Clear discoveries | Off | Forget every discovered mechanic on this character. Unticks itself once done |
| Log boss trigger ids | Off | Curation aid. Logs every animation, projectile and graphic id a tracked boss produces, and which mechanic claims it |

## Install

Not on the RuneLite Plugin Hub yet. To run it, build from source:

```bash
gradlew runClient
```

That launches a RuneLite client with the plugin sideloaded. Requires JDK 11+.

`BossMechanicsPluginTest` is a `main()` launcher, not a JUnit test, so
`gradlew test` will not run it. Launching it from IntelliJ instead needs `-ea` in
the VM options, since RuneLite refuses to start without assertions enabled.

## Adding a boss

Boss data is one hand-curated JSON file per boss in [`data/bosses/`](data/bosses/).
Adding a boss means adding a file and an index entry, no Java.

```bash
gradlew build
```

That runs the schema validation tests against every bundled data file, and fails
on a malformed file or an index that disagrees with the directory. A data-only PR
is green when those pass. See [data/SCHEMA.md](data/SCHEMA.md) for the field
tables and [docs/DECISIONS.md](docs/DECISIONS.md) for the design decisions behind
them.

## Data and attribution

Mechanic names, descriptions and counterplay are hand-written, researched from the
[Old School RuneScape Wiki](https://oldschool.runescape.wiki/) (CC BY-SA 3.0).
Every boss file links its source page.

Boss Mechanics is an unofficial plugin, not affiliated with Jagex or RuneLite.

## License

BSD-2-Clause. See [LICENSE](LICENSE).

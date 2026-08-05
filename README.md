# Boss Mechanics

A boss mechanics codex that lives inside the game. Every supported
boss's collection log page gets a **Boss Mechanics** button. It opens a mechanic list
and shows you: what the boss does, what you do about
it, and a preview.

Mechanics start locked. They unlock as your client watches the boss use them. (Or just hit 'View All' to see everything).

![The Boss Mechanics button on a collection log boss page](docs/boss-mechanic-hero.png)

## What it does

### Discovery

Mechanics are hidden with `???` until encountered in a fight.
When it is, the mechanic is broadcast in the game chat and 'unlocked' in the Boss Mechanics UI. 
Progress shows in a **Mechanics Discovered** bar across the top of the window.

Detection watches animations, projectiles, graphics and NPC spawns.

If you're not interested in the discovery loop **View All** reveals everything.

![Mechanic discovery messages in the game chat](docs/mechanic-discovery.png)

### Mechanic list and counter play

The left column lists the boss's mechanics in the order you (usually) meet them along with phase labels where appropriate. 
The right column holds the selected mechanic's description and its counter play. 
There is a **WIKI** button in the title bar that opens the boss's strategy page.

Selecting a mechanic plays the boss model performing that move.  Certain UI elements can't be displayed
as they are rendered by the engine during gameplay (ex., Doom's prayers or charge bar).  Will work on bridging those
gaps over time since they are critical parts of certain mechanics.

![The mechanic list, with a selected mechanic's description, counter play and preview](docs/move-list.gif)

### The window

The window uses the game's steel interface sprites and sits over the
collection log.  When opened but it is draggable by its title bar anywhere on screen.

## Supported bosses

| Boss | Mechanics |
|---|---|
| Abyssal Sire | 9 |
| Vorkath | 6 |
| Doom of Mokhaiotl | 11 |
| The Mad Angel | 5 |

## Settings

| Setting | Default | What it does |
|---|---|---|
| Discovery chat messages | On | Announce each newly discovered mechanic in the chatbox |
| Clear discoveries | Off | Debug tool. Forget every discovered mechanic on this character. Unticks itself once done |
| Log boss trigger ids | Off | Debug tool. Logs every animation, projectile and graphic id a tracked boss produces, and which mechanic claims it |

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

Please file an issue for anything you'd like changed or submit a PR for adding new bosses/adjusting copy.

## Data and attribution

Mechanic names, descriptions and counter play are hand-written, researched from the
[Old School RuneScape Wiki](https://oldschool.runescape.wiki/) (CC BY-SA 3.0).
Every boss file links its source page.

Boss Mechanics is an unofficial plugin.

## License

BSD-2-Clause. See [LICENSE](LICENSE).

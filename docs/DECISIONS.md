# Boss Mechanics — design decisions

Locked during the planning grill on 2026-07-31. Revisit deliberately, not accidentally.

## Architecture

1. **External plugin, no RuneLite fork.** Built from the example-plugin template,
   compiles against published `net.runelite:client` artifacts. RuneLite source is
   reference reading only.
2. **In-game UI is the product.** No sidebar-panel version. The injected button and
   the custom interface are the whole point.
3. **Own window, not embedded.** The injected "Boss Mechanics" icon button on a
   boss's collection log page opens our own custom-widget interface styled to match
   the game. We never squat inside the collection log's widget tree (Jagex scripts
   rebuild it constantly). Only the single injected button touches their interface.
4. **Build for Plugin Hub from day one.** Public repo, BSD-2-Clause, hub-compliant
   structure. Develop and playtest sideloaded; submit when the launch set feels good.

## Data

5. **Hand-curated, wiki-researched.** No runtime scraping — wiki strategy pages are
   prose, not structured data. One JSON file per boss (see `data/SCHEMA.md`).
   Paraphrase + attribute (wiki text is CC BY-SA 3.0).
6. **Bundled at launch.** Data ships inside the plugin. Remote fetch (GitHub raw)
   only becomes worth it when boss count grows and data PRs outpace releases.
7. **Launch bosses (5):** Abyssal Sire, Zulrah, Vorkath, General Graardor,
   Mad Angel. Chosen to span boss archetypes, not maximize coverage. Raids are
   explicitly out of scope for launch. Adding boss #6 must be a pure data PR.
8. **Schema splits `description` (what the boss does) from `counterplay` (what you
   do).** Counterplay is 1-2 terse imperative sentences. No damage numbers or max
   hits — they age badly.

## Discovery

9. **Discovered = your client witnessed the trigger** (animation / projectile /
   graphic / npc-spawn fired while a boss NPC is present). You don't have to get hit.
10. **Two-state model: revealed vs discovered.** "View All" is a reversible reveal
    toggle for reading; the progress bar always shows genuine discoveries. A misclick
    can't destroy the discovery game. (Provisional — validate feel in playtesting.)
11. **Per-character state**, stored via ConfigManager RS-profile keys.
12. **Undiscovered mechanics show as locked "???" rows**, not hidden.
13. **Discovery announces itself** with a collection-log-style chatbox message:
    "Boss mechanic discovered: X." No popups mid-fight.

## Presentation

14. **Animations render live from the game cache** via a model widget playing the
    mechanic's animation ID. Known limitation: shows the boss's body animation only —
    no projectiles/AoE/adds. Mechanics that don't read from the body animation set
    `staticFallback` in the schema.
    **VALIDATED 2026-07-31 (issue #1 spike).** The Abyssal Sire renders in a custom
    widget and animations play and loop. Pre-rendered captures are no longer needed.
    What the spike established, which the UI work depends on:
    - Parent the model widget to top-level interface **child index 1**. Index 0 has
      identical dimensions and silently never draws; picking a parent by size fails.
    - Zoom ~3000 frames a large boss; 1000 is too far out to read.
    - The Sire is a **single** cache model, so no multi-model compositing is needed.
      Verify this per boss during curation — it is not guaranteed for every NPC.
    - Looping animations loop indefinitely; **one-shot animations (e.g. death) play
      once and the model then disappears.** Previews must prefer looping animation IDs,
      or re-trigger one-shots.
    - `getCanvasLocation()` reads `(-1,-1)` for hand-created dynamic children even
      while they render. Never use it as a visibility check; use `getRelativeX/Y`.
    - The interface tree is not always ready when the widget is first built; retry
      until the build yields widgets.
15. **Plugin name: "Boss Mechanics".** Injected button is a native-styled icon with a
    tooltip (not a text label). Button appears only on bosses that have data.

## Later decisions

Decision numbers are stable identifiers cited from GitHub issues and code comments,
so new decisions are appended here rather than inserted in a themed section.

16. **Boss discovery is a `data/bosses/index.json` list, not classpath directory
    scanning.** **VALIDATED 2026-07-31 (issue #2).** Scanning is unreliable across
    sideload vs Plugin Hub classloaders; a hardcoded boss enum would violate D7
    ("adding a boss is a pure data PR"). A test reconciles the index against the
    real directory listing so a forgotten index line fails the build. Belongs with
    the Data decisions above (it is the mechanism that enforces D7).
17. **Detection trigger semantics, locked with the engine (issue #7).** Animation
    triggers only match when played by an NPC currently tracked as a boss's
    presence (source gating kills player-collision false positives entirely).
    NPC transforms (`NpcChanged`) count as npc-spawn triggers, not a separate
    trigger kind: Sire phase forms transform, they don't spawn. Graphics report
    no source actor, so they gate on boss presence only — curators must pick
    graphic ids players can't produce themselves. Detection state (which
    mechanics have been witnessed) is in-memory only until persistence lands
    in #8.
18. **Discovery/reveal persistence, state layer only (issue #8).** Supersedes D17's
    closing sentence: detection state is no longer in-memory only. One key per boss per
    concern, stored via `ConfigManager` RS-profile keys (D11), CSV-valued:
    `discovered.<bossId>` holds sorted mechanic ids; `revealed.<bossId>` (the reveal half
    of D10) is present with value `"true"` iff revealed and unset (not `"false"`) when
    not. Unknown/stale ids inside a discovered CSV are simply never matched, so renaming
    or removing a mechanic id never needs a migration — deliberate, since D10 is still
    provisional. Write-through on every new discovery; load is `DiscoveryState.reload()`,
    called at the end of `startUp` and on `RuneScapeProfileChanged` (not
    `GameStateChanged LOGGED_IN`, which fires before the profile key resolves). Reload
    always replaces, never merges, so switching characters can't leak one character's
    discoveries into another's. Reveal is per-boss and persisted, not a global toggle — a
    global reveal would flip bosses the player never opened. The read-modify-write CSV
    update in `addDiscovered` is safe only because every write happens on the client
    thread; if #5 later writes from a Swing/UI thread this assumption breaks and the
    read-modify-write needs a lock or a client-thread hop. Interface consumption of this
    state (View All button, progress bar rendering) is #5's, not built here.

19. **Collection log button injection (issue #4).** The hook is `ScriptPostFired` for
    `ScriptID.COLLECTION_DRAW_LIST` (2731), which is how RuneLite core's
    `ChatCommandsPlugin` reads the collection log. `WidgetLoaded` on group 621 was
    rejected as the primary hook: it fires once when the log opens, not on tab switch,
    boss selection or search click. The button is parented to
    `InterfaceID.Collection.COMBAT_ACHIEVEMENTS`'s own parent, which sidesteps D14's
    parent-picking problem entirely: a sibling that visibly draws proves that layer
    draws, so no size heuristic is needed. No anchor means no button. Gating on the
    collection-log tab varbit was rejected because bosses do not all live under the Boss
    tab (TzTok-Jad is under Minigames); the page-title lookup *is* the tab filter. That
    lookup is `BossPageIndex`: normalized (colour tags stripped, whitespace collapsed,
    case folded) but otherwise exact against `Boss.name`, and a page we can't match
    silently gets no button. Deliberately no `collectionLogPages` alias array in the
    schema: `BossPageIndex` is the seam if a page title ever disagrees with the display
    name. Teardown is `setHidden(true)` plus dropping the reference: there is no
    single-child delete, and `deleteAllChildren()` would destroy Jagex's own children.
    Duplicate prevention is an identity scan of `parent.getDynamicChildren()` rather than
    a `button != null` check, because a Jagex rebuild of the header drops our child
    without telling us; the scan self-heals in both directions and keeps this to one
    dynamic child per collection log open. The click is a `Consumer<Boss>` supplied by the
    plugin, so the real interface (#5) replaces one lambda and not this class.

## Open questions

- Mad Angel is the newest boss; wiki/community documentation of its IDs may be thin.
  Curate it last.
- UX feel of revealed-vs-discovered needs playtesting (decision 10 is provisional).
- ~~Exact sprite for the injected button.~~ **RESOLVED (issue #4):**
  `net.runelite.api.gameval.SpriteID.ICON_SWORDS` (3029), a pair of crossed swords, as the
  closest stock combat glyph to the Combat Achievements trophy it sits beside. One
  constant, so swapping it after seeing it in game is a one-line change.

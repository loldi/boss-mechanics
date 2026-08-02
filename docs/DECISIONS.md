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

    **Confirmed in game 2026-08-01, and load-bearing for #5:**
    - Interface geometry can be read offline. The cache is at
      `~/.runelite/jagexcache/oldschool/LIVE` and `net.runelite:cache` parses it, so any
      interface's component sizes, position modes and sprites can be dumped without a
      client. This found the right-aligned position mode below before it cost a test run.
    - The Combat Achievements button uses `xPositionMode 2`, so x is measured from the
      **right** edge and a larger x sits further **left**. Absolute-left maths sends a
      widget off the opposite side of the screen.
    - What that button looks like is not in the cache: it is a bare 50x25 layer whose
      children are built at runtime. It is a nine-slice frame (corners 913/914/915/916,
      edges 917/919 and 918/920) over a stretched background fill (297), with an 18x17
      icon at (16,3). Our button reuses all nine frame sprites, so only the glyph differs.
    - **Nested dynamic children render.** Our button is a dynamic `LAYER` whose own
      dynamic `GRAPHIC` children draw correctly. #5's window can therefore be built as a
      tree rather than as a flat pile of siblings with computed offsets.
    - The cache ships no boss-mechanics glyph. Every sprite that fits 18x17 is something
      unrelated (SAVE, LOAD, WORLDSWITCHER_FILTER), so a custom sprite bundled as a plugin
      resource is the eventual answer; `SPRITE_ICON` is the single constant to change.

20. **Boss Mechanics window (issue #5).** The window is a 500x314 panel (the collection log's own
    UNIVERSE size, 621 child 88) hosted on a **top-level root the client draws after GAMEFRAME**
    (see the correction at the end of this decision), not on anything inside the collection log,
    which is what keeps D3 intact. The first attempt used FLOATER, a later sibling of MAINMODAL under
    the same parent in all six top-level layouts (548, 161, 164, 165, 601, 80), so it draws on
    top of the log, and it is `w=0 h=0` with both size modes MINUS in all six, so it always
    resolves to full parent size and cannot clip a child to nothing. `TopLevelFloater` is the
    lookup, and it is unit tested, a useful correction to `CollectionLogButton`'s claim that
    nothing in `ui` is testable. `client.openInterface` was rejected (it needs a real cache group
    we cannot add, and no class in client-1.12.33 calls it); parenting into the log's own tree was
    rejected outright as a D3 violation.

    - **`com.bossmechanics.view` is a third RuneLite-free package.** `MechanicsView.of(boss,
      state, revealed)` resolves every row's final strings up front, so the interface positions
      rectangles and copies text and decides nothing. **The D10 invariant lives here**:
      `discoveredCount()` counts rows where `discovered`, and `locked` is the separate
      `!discovered && !revealed`. The interface never counts anything, so "View All" physically
      cannot inflate the progress bar.
    - **The progress bar is the Combat Achievements bar, script 4782**, rebuilt as its five
      children in draw order: inner border rectangle `0x474645`, empty track (sprite 3392, 1x27,
      tiled), fill (sprite 3391, tiled) at `done * (w-4) / total`, centred shadowed PLAIN_12
      label, outer border rectangle `0x0E0E0C`. Rectangle colours go through `setTextColor`.
    - **The scrollbar is built by hand, not by `runScript(31, ...)`.** Disassembling the injected
      client's `createChild` shows a dynamic child's `id` field is copied straight from its
      parent, so `Widget.getId()` on anything in our tree returns the FLOATER's component id.
      Handing that to Jagex's script 31 (or to `ScriptID.UPDATE_SCROLLBAR`) would make their
      script delete and rebuild the FLOATER's children, destroying our own window root. **No
      dynamic widget of ours can be addressed by a clientscript that takes a component id**,
      which is worth remembering for #6. The cache's own scrollbar sprites are reused (track 792, thumb
      789/790/791, arrows 773/788, all 16 wide), the arrows and the mouse wheel scroll, and the
      thumb indicates position without being draggable. Wheel rotation arrives as
      `ScriptEvent.getMouseY()`, which is how core RuneLite's bank tag tabs read it.
    - **Escape is consumed by the window.** One press closes the window, a second closes the
      collection log, matching nested game interfaces. The key listener is registered only while
      the window is open. Key events are AWT-thread, so the close hops to the client thread.
    - **Locked rows show no phase tag**, just `???`. Pure "???" is cleaner and makes the reveal
      deliver something.
    - **"View All" persists** per boss per character through `ProfileStateStore` (D18), because
      D10 calls reveal a reading mode rather than a momentary peek. Recorded here since D10 is
      still provisional. The write happens in a widget op listener, which is the client thread,
      which is the condition D18 named as necessary for that store's read-modify-write.
    - Teardown hides the root and empties it, but keeps the reference so reopening reuses the one
      dynamic child instead of stranding a hidden layer on the FLOATER every time; the reference
      is still dropped untouched on the transitions that destroy the interface tree, and
      re-checked by identity scan before reuse (D19).
    - The nine-slice frame is duplicated from `CollectionLogButton` on purpose. Sharing it means
      restructuring that class, which is a follow-up once #6 lands.
    - Row heights are fixed per state (22 locked, 68 discovered) because OSRS text widgets wrap
      but expose no wrapped height; scroll height is the sum of the actual row heights.
    - **CORRECTED IN GAME 2026-08-02: the host is UI_HIGHLIGHTS, not FLOATER.** The magenta
      backdrop earned its keep on the first run: the window drew *behind* the collection log.
      The cache reasoning above was wrong in a way only the runtime tree reveals. The log's
      actual parent chain is
      `GAMEFRAME(c34) <- c94 <- c15 <- FLOATER(c18) <- 621:c87 <- 621:c88`, i.e. **the
      collection log is opened onto FLOATER itself**, and FLOATER is nested inside GAMEFRAME
      rather than being its sibling. A dynamic child of FLOATER therefore sits under the
      interface node opened onto it.
      The client's actual root draw order was `c0, GAMEFRAME(c34), c35, c36, MOUSEOVER(c37),
      UI_HIGHLIGHTS(c98)`. Anything under a root after GAMEFRAME draws over the whole collection
      log. Of those, c35 is the 300-wide sidebar and c36 is 1x1, so neither can hold a 500x314
      window; MOUSEOVER and UI_HIGHLIGHTS are both full parent size. **UI_HIGHLIGHTS is drawn
      last and only carries occasional highlight overlays, whereas MOUSEOVER backs hover
      rendering**, so UI_HIGHLIGHTS is the one to squat. It exists in all six top-level layouts.
      D3 still holds: it is not the collection log's tree.
      The general lesson, which cost one run to learn: **cache sibling order does not predict
      runtime draw order.** Only `getWidgetRoots()` and a live parent chain do.

## Open questions

- Mad Angel is the newest boss; wiki/community documentation of its IDs may be thin.
  Curate it last.
- UX feel of revealed-vs-discovered needs playtesting (decision 10 is provisional).
- ~~Exact sprite for the injected button.~~ **RESOLVED (issue #4):**
  `net.runelite.api.gameval.SpriteID.ICON_SWORDS` (3029), a pair of crossed swords, as the
  closest stock combat glyph to the Combat Achievements trophy it sits beside. One
  constant, so swapping it after seeing it in game is a one-line change.

# Boss data schema

One JSON file per boss in `data/bosses/`. Hand-curated. The OSRS wiki is the
research source; every boss file links back to it for attribution (wiki text is
CC BY-SA 3.0, so we paraphrase and attribute, never copy).

## Adding a boss

1. Add `data/bosses/<id>.json` following the field tables below.
2. Add `<id>` as a new entry in `data/bosses/index.json` (a JSON array of ids).
   This is the discovery list the plugin reads at startup; scanning the
   classpath directory directly was rejected as unreliable across the
   sideload and Plugin Hub classloaders (see docs/DECISIONS.md).
3. Run `gradlew build`. `BundledBossDataTest` fails if the new file breaks the
   schema, or if the index and the real directory listing disagree (a
   forgotten index line, or an index entry with no matching file).

## Boss file

| Field | Type | Notes |
|---|---|---|
| `id` | string | kebab-case slug, matches filename (`abyssal-sire`) |
| `name` | string | Display name. **Load-bearing**: it is matched against the collection log page title to decide whether this boss gets a Boss Mechanics button, so a name that doesn't match the page means no button, silently. Matching ignores case, surrounding whitespace and colour tags, but is otherwise exact. |
| `npcIds` | int[] | All NPC IDs for this boss (forms/phases) — used to know we're "in the fight" |
| `wikiUrl` | string | Boss strategy page used for research |
| `mechanics` | Mechanic[] | Ordered as they should appear in the list |

## Mechanic

| Field | Type | Notes |
|---|---|---|
| `id` | string | kebab-case, unique within the boss |
| `name` | string | Short move name, e.g. "Acid Pool Barrage" |
| `description` | string | What the boss does. 1-3 sentences. |
| `counterplay` | string | What YOU do. **1-2 terse imperative sentences max.** If it needs a paragraph, split the mechanic. |
| `phase` | string? | Optional, e.g. "Phase 2 only" |
| `detection` | Trigger[] | Any trigger firing while a boss NPC is present = discovered |
| `preview` | Preview | What the animation column shows |
| `wikiUrl` | string? | Optional deep link if different from the boss page |

## Trigger

| Field | Type | Notes |
|---|---|---|
| `type` | string | `animation` \| `projectile` \| `graphic` \| `npc-spawn` |
| `id` | int | The game ID for that type |

**Detection semantics (docs/DECISIONS.md D17):** `animation` only matches when
played by an NPC the engine is currently tracking as this boss (a nearby player's
animation never matches). An NPC transform counts as an `npc-spawn` trigger too —
if a boss's phase form changes id rather than spawning a new NPC, curate it as
`npc-spawn` on that new id. `graphic` triggers have no source actor to check, so
they only gate on the boss being present; pick graphic ids players can't produce
themselves, or the trigger will false-positive.

## Preview

| Field | Type | Notes |
|---|---|---|
| `animationId` | int? | Animation played on the boss model in the viewer. **Ignored when `staticFallback` is true** — a static pose always wins, even if this is also set. Required when `staticFallback` is false (or absent); the loader rejects a mechanic with neither. |
| `npcId` | int? | Which NPC model to show (defaults to first of `npcIds`) |
| `staticFallback` | bool | true when the body animation alone doesn't read (projectile/AoE mechanics) and a static model pose should be shown instead of a looping animation. Defaults to `false` when the field is absent. **Prefer the npc's own standing/idle animation over a raw static pose** (docs/DECISIONS.md D26) — the Combat Achievements screen always plays an animation, and a static pose renders the model's unposed bind pose (a T-pose for most humanoids), which reads as broken. A raw pose is the last resort for an npc with no usable idle, and sits on the box's own vertical centre line unless `shiftY` compensates. |
| `zoom` | int? | Model widget zoom, fit per mechanic (docs/DECISIONS.md D25). Defaults to 3000 (spike-validated for a large boss, but not a good fit for any specific mechanic) when absent — **no animated preview ships without an explicit zoom**. Compute it offline across the animation's full frame range: `zoom = 512 * max(2 * maxAbsX / 291, (heightAbove + 2 * heightBelow) / 140)`, plus a 10% margin, where the box is 291 wide and 140 tall. A static pose (`staticFallback: true`) is less sensitive to a wrong zoom (nothing moves), but still curate one so a boss switch doesn't visibly jump scale between mechanics. |
| `shiftY` | int? | Vertical anchor correction, **in pixels** (docs/DECISIONS.md D26) — it is consumed directly as `setOriginalHeight(MODEL_HEIGHT + 2*shiftY)` on the pool widget, so a model-unit value baked in here without converting first double-counts the projection and can push the rect far outside the 140px box. Defaults to 0 (no correction) when absent. The engine anchors the model's ground line (y=0), not the centre of its animated bounds, at the widget's vertical centre, so a model that extends mostly upward from the ground rides high in the box unless compensated. Compute it alongside zoom, from the same per-frame extents: `shiftY_units = -(minY + maxY) / 2` across the animation's full frame range (or the static pose), where `minY`/`maxY` are the model's own combined vertical extents in MODEL UNITS — then project to pixels the same way zoom does, `shiftY = round(shiftY_units * 512 / zoom)`, using the actual zoom this preview ships with. A correctly-projected value can never exceed 70 (half the 140px box); `BundledBossDataTest` guards this. The cachetool's `FitZoom` emits `shiftY_units` (not yet projected) alongside zoom; a curator does the `* 512 / zoom` conversion before pasting the value in. |
| `modelId` | int? | Explicit cache model id, bypassing the `npcId` lookup entirely (docs/DECISIONS.md D27). Curated for a model that has no npc to look it up from (e.g. a base spotanim model) or that a curator otherwise wants to pin directly. Still needs an `animationId` (or `staticFallback`) to say what plays on it. Defaults to absent, i.e. resolve the model from `npcId` as usual. |
| `sprite` | string? | Bundled sprite resource name under `src/main/resources/sprites/` (docs/DECISIONS.md D27), e.g. `"venomous-dragonfire.png"`. **Wins over every other field in this table** — when present, none of them are read, and no validator error requires `animationId`/`staticFallback` either. For the rare case where the game cache genuinely has no distinctly-coloured model to render (colour applied only as a spotanim-level recolor the Widget API can't reach). Must be exactly 287x136, binary alpha, no opaque `#000000` (see docs/DECISIONS.md D27 for the full image spec). Defaults to absent. |
| `shiftX` | int? | Horizontal anchor offset, **in pixels** (docs/DECISIONS.md D27). Mirrors `shiftY`'s mechanism sideways: the pool widget's rect grows by `2 * abs(shiftX)` and its origin moves by `shiftX - abs(shiftX)`, so the model's own centre moves without the rect ever losing coverage of the box. Defaults to 0 (centred) when absent. |
| `secondary` | object? | A second model shown beside this one (docs/DECISIONS.md D27, shape (a)): `{modelId\|npcId, animationId, zoom, shiftX, shiftY, rotationX, rotationY, rotationZ}`, resolved with the same `modelId`/`npcId` precedence as the primary, in its own widget. Defaults to absent (no secondary). |
| `rotationX` | int? | Model rotation about its own X axis, **0-2047 per axis** (a full turn), docs/DECISIONS.md D28. Defaults to 0 (unrotated, D23's spike-validated value) when absent. A value outside 0-2047 crashes the client, so `BossDataValidator` rejects it at curation time. **`FitZoom` does not model rotation** — its extents assume rotation 0, so a mechanic that curates a non-zero rotation needs its `zoom`/`shiftY`/`shiftX` re-eyeballed in game rather than trusted from the tool's output. |
| `rotationY` | int? | Same range/default/caveat as `rotationX`, about the Y axis. |
| `rotationZ` | int? | Same range/default/caveat as `rotationX`, about the Z axis. |

**Preview resolution order (the preference ladder, docs/DECISIONS.md D27): `sprite` > `modelId` >
`npcId`+`animationId` > the npc's own idle animation > a raw static pose (`staticFallback`).** Only
the first of these actually present is used; everything below it in the ladder is ignored, with no
validator error. **The preview npc need not be the trigger npc** — curate whichever variant actually
carries the visible model/animation. Abyssal Sire's `respiratory-systems` is the example: detection
stays on npc-spawn 5914 (the id the game actually spawns), but the preview targets npc 5915, the
distinct npc that is actually animated in the fight; 5914 itself is an unanimatable 8-vertex
clickbox placeholder. **The animation itself can come from a still different cache entity than the
npc being previewed, provided they share a model** (docs/DECISIONS.md D28) — `respiratory-systems`
keeps `npcId: 5915` but its `animationId` (7105) is borrowed from a scenery object that shares
5915's model and is what actually animates in the fight; an animation id has no enforced tie to
the entity a gameval name suggests it "belongs" to, it is just a sequence applied to whatever model
is loaded.

**The recolor-table rule (docs/DECISIONS.md D28): an npc whose recolor table covers its entire
model cannot be model-previewed colour-true — curate a `sprite` instead.** There is no Widget API
path to apply an npc's own recolor table to a live model widget (docs/DECISIONS.md D27's survey
still stands: `setModelId` takes a bare cache id, and `client.loadModelData(id).recolor(...)`'s
output only ever attaches to a scene `RuneLiteObject`, never a widget). Vorkath's zombified spawn
(npc 8063, model 35025) is the example: its recolor table replaces 100% of the model's faces
(`30123`/`30238`/`29590`, i.e. `#307B69`/`#1A5D4D`/`#02553A`), so a raw model preview would render
the cache's base texture colour, not this. Check an npc's recolor coverage before curating a model
preview for it; full coverage means a bundled sprite, not `modelId`/`npcId`.

## Style rules

- Counterplay is imperative voice: "Keep moving." not "The player should keep moving."
- No damage numbers, max hits, or attack-style stats. They age badly with rebalances.
- Descriptions are paraphrased, never lifted from the wiki verbatim.

## Curation notes

Underscore-prefixed fields (e.g. `_note`) are ignored by the loader and may be
added freely for curation notes, like `vorkath.json`'s `_note` flagging its IDs
as unverified. The loader tolerates any unknown field, not just underscore-prefixed
ones, but that's the convention for notes meant to be read, not just parsed past.

### Finding trigger ids

Turn on **Log boss trigger ids** in the plugin config and fight the boss. Every
distinct animation, projectile and graphic it produces is logged once with the
mechanic that claims it, or `UNCLAIMED`.

Two rules learned the hard way while curating Vorkath:

**A gameval constant name describes one known use of an id, not an exclusive
one.** The game reuses spotanims heavily. Vorkath's dragonfire impact graphics
come through as `FIRESURGE_IMPACT` and `FIREBLAST_IMPACT`, which reads as though
the player cast them. Ownership was settled by timestamps, not names: those ids
landed one to two seconds after Vorkath's dragonfire projectiles, in the same
order, across two separate fights. **Correlate the log by time before believing a
name.**

**Graphics can't be attributed to an actor.** Projectiles usually name a source,
graphics never do, so a graphic trigger fires on boss presence alone. Only use one
when the id is something a player cannot produce. Vorkath's dragonfire impacts are
deliberately not triggers for exactly this reason: a player casting Fire Surge
nearby would unlock the mechanic.

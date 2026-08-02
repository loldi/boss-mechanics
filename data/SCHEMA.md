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
| `staticFallback` | bool | true when the body animation alone doesn't read (projectile/AoE mechanics) and a static model pose should be shown instead of a looping animation. Defaults to `false` when the field is absent. |
| `zoom` | int? | Model widget zoom. Defaults to 3000 (spike-validated for a large boss) when absent. |

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

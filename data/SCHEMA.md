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
| `name` | string | Display name, matches collection log page name |
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

## Preview

| Field | Type | Notes |
|---|---|---|
| `animationId` | int? | Animation played on the boss model in the viewer |
| `npcId` | int? | Which NPC model to show (defaults to first of `npcIds`) |
| `staticFallback` | bool | true when the body animation alone doesn't read (projectile/AoE mechanics) and a static illustration should be used instead. Defaults to `false` when the field is absent. |

## Style rules

- Counterplay is imperative voice: "Keep moving." not "The player should keep moving."
- No damage numbers, max hits, or attack-style stats. They age badly with rebalances.
- Descriptions are paraphrased, never lifted from the wiki verbatim.

## Curation notes

Underscore-prefixed fields (e.g. `_note`) are ignored by the loader and may be
added freely for curation notes, like `vorkath.json`'s `_note` flagging its IDs
as unverified. The loader tolerates any unknown field, not just underscore-prefixed
ones, but that's the convention for notes meant to be read, not just parsed past.

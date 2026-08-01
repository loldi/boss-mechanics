package com.bossmechanics.detection;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.Mechanic;
import com.bossmechanics.data.Trigger;
import com.bossmechanics.data.TriggerType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Pure detection core: no RuneLite dependency, so it is plain-JUnit testable (see
 * docs/DECISIONS.md D17). Tracks which boss NPCs are currently present via live spawn events,
 * and matches witnessed triggers against a per-trigger-type index built once at construction.
 * Every match is confirmed against {@link DiscoveryState} so a mechanic is only ever returned
 * once.
 */
public class DetectionEngine
{
	private final DiscoveryState state;

	/** boss npcId -> owning boss id, for recognizing a spawn as one of a tracked boss's forms. */
	private final Map<Integer, String> npcIdToBossId = new HashMap<>();

	/** trigger type -> trigger id -> mechanics it can discover. Built once, O(1) lookups per event. */
	private final Map<TriggerType, Map<Integer, List<Discovery>>> triggerIndex = new HashMap<>();

	/** Live npcIndex -> bossId for NPCs currently on screen that belong to a tracked boss. */
	private final Map<Integer, String> presence = new HashMap<>();

	public DetectionEngine(List<Boss> bosses, DiscoveryState state)
	{
		this.state = state;

		for (Boss boss : bosses)
		{
			for (Integer npcId : boss.getNpcIds())
			{
				npcIdToBossId.put(npcId, boss.getId());
			}

			for (Mechanic mechanic : boss.getMechanics())
			{
				Discovery discovery = new Discovery(boss, mechanic);
				for (Trigger trigger : mechanic.getDetection())
				{
					TriggerType type = trigger.triggerType();
					if (type == null)
					{
						continue;
					}

					triggerIndex
						.computeIfAbsent(type, t -> new HashMap<>())
						.computeIfAbsent(trigger.getId(), id -> new ArrayList<>())
						.add(discovery);
				}
			}
		}
	}

	/**
	 * A boss NPC appeared. Presence updates before matching (docs/DECISIONS.md D17): an id that
	 * is simultaneously a boss form and an npc-spawn trigger (e.g. Sire phase forms) must count
	 * as present for its own trigger match in this same call.
	 */
	public List<Discovery> npcSpawned(int npcIndex, int npcId)
	{
		updatePresence(npcIndex, npcId);
		return matchNpcSpawn(npcId);
	}

	/** A tracked NPC transformed. Counts as an npc-spawn trigger (Sire phase forms transform, they don't spawn). */
	public List<Discovery> npcChanged(int npcIndex, int newNpcId)
	{
		updatePresence(npcIndex, newNpcId);
		return matchNpcSpawn(newNpcId);
	}

	/** Matches only if {@code npcIndex} is a currently-tracked boss NPC, and only against that boss's mechanics. */
	public List<Discovery> animationPlayed(int npcIndex, int animationId)
	{
		String bossId = presence.get(npcIndex);
		if (bossId == null)
		{
			return Collections.emptyList();
		}

		return matchGated(TriggerType.ANIMATION, animationId, ownedBy(bossId));
	}

	/**
	 * Matches if the projectile's source is a tracked boss NPC. A null source (the game didn't
	 * report one) falls back to gating on any tracked boss being present.
	 */
	public List<Discovery> projectileFired(Integer sourceNpcIndex, int projectileId)
	{
		if (sourceNpcIndex != null)
		{
			String bossId = presence.get(sourceNpcIndex);
			if (bossId == null)
			{
				return Collections.emptyList();
			}
			return matchGated(TriggerType.PROJECTILE, projectileId, ownedBy(bossId));
		}

		return matchGated(TriggerType.PROJECTILE, projectileId, anyBossPresent());
	}

	/** Graphics report no source actor, so they gate on boss presence only (curators must pick ids players can't produce). */
	public List<Discovery> graphicCreated(int graphicId)
	{
		return matchGated(TriggerType.GRAPHIC, graphicId, anyBossPresent());
	}

	/** A tracked NPC left. */
	public void npcDespawned(int npcIndex)
	{
		presence.remove(npcIndex);
	}

	/** Clears all tracked presence, e.g. on logout/world-hop. Discovery state is untouched. */
	public void clearPresence()
	{
		presence.clear();
	}

	/** True when any curated mechanic lists this trigger. Lets callers tell "no mechanic uses this id" apart from "already discovered", which both return no discoveries. */
	public boolean isKnownTrigger(TriggerType type, int triggerId)
	{
		Map<Integer, List<Discovery>> byId = triggerIndex.get(type);
		return byId != null && byId.containsKey(triggerId);
	}

	/** Boss id of a tracked NPC index, or null if that index isn't a boss we follow. */
	public String trackedBossId(int npcIndex)
	{
		return presence.get(npcIndex);
	}

	/** Any tracked boss on screen. Projectiles and graphics often report no source actor, so this is the only gate available for them. */
	public String anyTrackedBossId()
	{
		return presence.isEmpty() ? null : presence.values().iterator().next();
	}

	private List<Discovery> matchNpcSpawn(int npcId)
	{
		return matchGated(TriggerType.NPC_SPAWN, npcId, anyBossPresent());
	}

	private void updatePresence(int npcIndex, int npcId)
	{
		String bossId = npcIdToBossId.get(npcId);
		if (bossId != null)
		{
			presence.put(npcIndex, bossId);
		}
	}

	private List<Discovery> matchGated(TriggerType type, int triggerId, Predicate<Discovery> gate)
	{
		Map<Integer, List<Discovery>> byId = triggerIndex.get(type);
		List<Discovery> candidates = byId == null ? null : byId.get(triggerId);
		if (candidates == null)
		{
			return Collections.emptyList();
		}

		List<Discovery> result = new ArrayList<>();
		for (Discovery candidate : candidates)
		{
			if (gate.test(candidate)
				&& state.markDiscovered(candidate.getBoss().getId(), candidate.getMechanic().getId()))
			{
				result.add(candidate);
			}
		}
		return result;
	}

	private static Predicate<Discovery> ownedBy(String bossId)
	{
		return candidate -> candidate.getBoss().getId().equals(bossId);
	}

	private Predicate<Discovery> anyBossPresent()
	{
		return candidate -> presence.containsValue(candidate.getBoss().getId());
	}
}

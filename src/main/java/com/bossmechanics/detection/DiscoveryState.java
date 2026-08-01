package com.bossmechanics.detection;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.Mechanic;
import java.util.HashSet;
import java.util.Set;

/**
 * Record of which boss mechanics have been witnessed. In-memory for fast lookups during
 * detection; write-through to a {@link DiscoveryStore} on every new discovery, and replaced
 * wholesale from the store on {@link #reload()} (docs/DECISIONS.md D18). The no-arg
 * constructor keeps the pre-#8 in-memory-only behavior (backed by {@link DiscoveryStore#NOOP}),
 * so callers that never persist state see no change.
 */
public class DiscoveryState
{
	private final DiscoveryStore store;
	private final Set<String> discovered = new HashSet<>();

	public DiscoveryState()
	{
		this(DiscoveryStore.NOOP);
	}

	public DiscoveryState(DiscoveryStore store)
	{
		this.store = store;
	}

	/** @return true if this pair had not been discovered before this call (newly discovered). */
	public boolean markDiscovered(String bossId, String mechanicId)
	{
		boolean isNew = discovered.add(key(bossId, mechanicId));
		if (isNew)
		{
			store.addDiscovered(bossId, mechanicId);
		}
		return isNew;
	}

	public boolean isDiscovered(String bossId, String mechanicId)
	{
		return discovered.contains(key(bossId, mechanicId));
	}

	/**
	 * Clears in-memory state and reloads from the store. Replace, never merge: a character
	 * switch must not let one character's discoveries leak into another's.
	 */
	public void reload()
	{
		discovered.clear();
		discovered.addAll(store.loadDiscovered());
	}

	/**
	 * Forgets every discovery for one boss, in memory and in the store, so its mechanics
	 * announce again next time they're witnessed.
	 *
	 * @return how many were forgotten
	 */
	public int clearDiscovered(String bossId)
	{
		String prefix = bossId + ":";
		int before = discovered.size();
		discovered.removeIf(entry -> entry.startsWith(prefix));
		store.clearDiscovered(bossId);
		return before - discovered.size();
	}

	/** @return how many of {@code boss}'s current mechanics are discovered; stale/removed ids in the store are never counted. */
	public int discoveredCount(Boss boss)
	{
		int count = 0;
		for (Mechanic mechanic : boss.getMechanics())
		{
			if (isDiscovered(boss.getId(), mechanic.getId()))
			{
				count++;
			}
		}
		return count;
	}

	/**
	 * The one place the stored key format is defined. Every producer of these keys must
	 * call this: if a second copy of the format drifts, reload() returns keys that
	 * isDiscovered never matches, so every character silently loads zero discoveries
	 * while the tests stay green.
	 */
	public static String key(String bossId, String mechanicId)
	{
		return bossId + ":" + mechanicId;
	}
}

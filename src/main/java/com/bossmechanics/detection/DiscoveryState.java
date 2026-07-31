package com.bossmechanics.detection;

import java.util.HashSet;
import java.util.Set;

/**
 * In-memory record of which boss mechanics have been witnessed this session. The persistence
 * seam for #8: a {@code Set<String>} of {@code "bossId:mechanicId"} keys today, backed by
 * {@code ConfigManager} RS-profile keys later, without changing this class's callers.
 */
public class DiscoveryState
{
	private final Set<String> discovered = new HashSet<>();

	/** @return true if this pair had not been discovered before this call (newly discovered). */
	public boolean markDiscovered(String bossId, String mechanicId)
	{
		return discovered.add(key(bossId, mechanicId));
	}

	public boolean isDiscovered(String bossId, String mechanicId)
	{
		return discovered.contains(key(bossId, mechanicId));
	}

	private static String key(String bossId, String mechanicId)
	{
		return bossId + ":" + mechanicId;
	}
}

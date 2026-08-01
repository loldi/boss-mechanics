package com.bossmechanics;

import com.bossmechanics.detection.DiscoveryState;
import com.bossmechanics.detection.DiscoveryStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

/**
 * The only RuneLite-coupled discovery/reveal persistence class (docs/DECISIONS.md D18): a
 * humble object whose adapter bodies are straight get/set against {@link ConfigManager}
 * RS-profile keys, with all real logic (CSV codec) in plain, separately-tested static methods.
 *
 * <p>One key per boss per concern, CSV-valued: {@code discovered.<bossId>} holds sorted
 * mechanic ids; {@code revealed.<bossId>} is present (value {@code "true"}) iff revealed and
 * unset (not {@code "false"}) otherwise. Unknown/stale ids inside a discovered CSV are simply
 * never matched by {@code DiscoveryState}, so renaming or removing a mechanic never requires a
 * migration.
 */
public class ProfileStateStore implements DiscoveryStore
{
	private static final String DISCOVERED_PREFIX = "discovered.";
	private static final String REVEALED_PREFIX = "revealed.";
	private static final String REVEALED_VALUE = "true";

	private final ConfigManager configManager;
	private final Collection<String> bossIds;

	public ProfileStateStore(ConfigManager configManager, Collection<String> bossIds)
	{
		this.configManager = configManager;
		this.bossIds = bossIds;
	}

	@Override
	public Set<String> loadDiscovered()
	{
		Set<String> result = new HashSet<>();
		for (String bossId : bossIds)
		{
			String csv = configManager.getRSProfileConfiguration(BossMechanicsConfig.GROUP, discoveredKey(bossId));
			for (String mechanicId : parseCsv(csv))
			{
				result.add(DiscoveryState.key(bossId, mechanicId));
			}
		}
		return result;
	}

	@Override
	public void addDiscovered(String bossId, String mechanicId)
	{
		String key = discoveredKey(bossId);
		Set<String> current = parseCsv(configManager.getRSProfileConfiguration(BossMechanicsConfig.GROUP, key));
		current.add(mechanicId);
		configManager.setRSProfileConfiguration(BossMechanicsConfig.GROUP, key, toCsv(current));
	}

	@Override
	public void clearDiscovered(String bossId)
	{
		configManager.unsetRSProfileConfiguration(BossMechanicsConfig.GROUP, discoveredKey(bossId));
	}

	/** Every boss this store was built for, so callers can clear all of them. */
	public Collection<String> bossIds()
	{
		return bossIds;
	}

	public boolean isRevealed(String bossId)
	{
		return configManager.getRSProfileConfiguration(BossMechanicsConfig.GROUP, revealedKey(bossId)) != null;
	}

	/** false unsets the key entirely, rather than writing "false" (docs/DECISIONS.md D18). */
	public void setRevealed(String bossId, boolean revealed)
	{
		String key = revealedKey(bossId);
		if (revealed)
		{
			configManager.setRSProfileConfiguration(BossMechanicsConfig.GROUP, key, REVEALED_VALUE);
		}
		else
		{
			configManager.unsetRSProfileConfiguration(BossMechanicsConfig.GROUP, key);
		}
	}

	private static String discoveredKey(String bossId)
	{
		return DISCOVERED_PREFIX + bossId;
	}

	private static String revealedKey(String bossId)
	{
		return REVEALED_PREFIX + bossId;
	}

	/** @return the CSV's entries, trimmed with blanks skipped; never null. */
	static Set<String> parseCsv(String csv)
	{
		Set<String> result = new HashSet<>();
		if (csv == null || csv.isEmpty())
		{
			return result;
		}

		for (String part : csv.split(","))
		{
			String trimmed = part.trim();
			if (!trimmed.isEmpty())
			{
				result.add(trimmed);
			}
		}
		return result;
	}

	/** @return entries sorted and comma-joined; empty string for an empty set. */
	static String toCsv(Set<String> values)
	{
		List<String> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		return String.join(",", sorted);
	}
}

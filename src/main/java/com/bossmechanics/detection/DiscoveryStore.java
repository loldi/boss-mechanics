package com.bossmechanics.detection;

import java.util.Collections;
import java.util.Set;

/**
 * The persistence seam for {@link DiscoveryState} (docs/DECISIONS.md D18). Deliberately
 * RuneLite-free so this package stays plain-JUnit testable; the one real implementation
 * ({@code com.bossmechanics.ProfileStateStore}) lives outside this package.
 */
public interface DiscoveryStore
{
	/** @return every discovered pair as a {@code "bossId:mechanicId"} string. */
	Set<String> loadDiscovered();

	/** Called once per genuinely new discovery; never for a repeat. */
	void addDiscovered(String bossId, String mechanicId);

	/** Backs {@link DiscoveryState}'s no-arg constructor: nothing to load, nothing to write. */
	DiscoveryStore NOOP = new DiscoveryStore()
	{
		@Override
		public Set<String> loadDiscovered()
		{
			return Collections.emptySet();
		}

		@Override
		public void addDiscovered(String bossId, String mechanicId)
		{
		}
	};
}

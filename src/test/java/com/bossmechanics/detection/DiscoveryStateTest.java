package com.bossmechanics.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.Mechanic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class DiscoveryStateTest
{
	private DiscoveryState state;

	@Before
	public void setUp()
	{
		state = new DiscoveryState();
	}

	@Test
	public void firstMarkIsNewSecondIsNot()
	{
		assertTrue(state.markDiscovered("abyssal-sire", "miasma-pools"));
		assertFalse(state.markDiscovered("abyssal-sire", "miasma-pools"));
	}

	@Test
	public void isDiscoveredFlipsAfterMarking()
	{
		assertFalse(state.isDiscovered("abyssal-sire", "miasma-pools"));

		state.markDiscovered("abyssal-sire", "miasma-pools");

		assertTrue(state.isDiscovered("abyssal-sire", "miasma-pools"));
	}

	@Test
	public void distinctBossMechanicPairsAreIndependent()
	{
		state.markDiscovered("abyssal-sire", "miasma-pools");

		assertFalse(state.isDiscovered("abyssal-sire", "spawn-summon"));
		assertFalse(state.isDiscovered("vorkath", "miasma-pools"));
	}

	@Test
	public void markDiscoveredWritesThroughOnceNotOnRepeat()
	{
		FakeDiscoveryStore store = new FakeDiscoveryStore();
		DiscoveryState storeBacked = new DiscoveryState(store);

		storeBacked.markDiscovered("abyssal-sire", "miasma-pools");
		storeBacked.markDiscovered("abyssal-sire", "miasma-pools");

		assertEquals(1, store.writes.size());
	}

	@Test
	public void reloadReplacesStateFromStore()
	{
		FakeDiscoveryStore store = new FakeDiscoveryStore();
		DiscoveryState storeBacked = new DiscoveryState(store);
		storeBacked.markDiscovered("abyssal-sire", "miasma-pools");

		// Simulate a character switch: the store now belongs to a different profile entirely.
		store.data.clear();
		store.data.add("vorkath:zombified-spawn");

		storeBacked.reload();

		assertFalse("character A's discovery must not survive a reload", storeBacked.isDiscovered("abyssal-sire", "miasma-pools"));
		assertTrue(storeBacked.isDiscovered("vorkath", "zombified-spawn"));
	}

	@Test
	public void discoveredCountIgnoresStaleIds()
	{
		FakeDiscoveryStore store = new FakeDiscoveryStore();
		store.data.add("test-boss:m1");
		store.data.add("test-boss:m2");
		store.data.add("test-boss:removed-mech");

		DiscoveryState storeBacked = new DiscoveryState(store);
		storeBacked.reload();

		Boss boss = new Boss("test-boss", "Test Boss", Collections.singletonList(1), "https://example.com",
			Arrays.asList(mechanic("m1"), mechanic("m2"), mechanic("m3")));

		assertEquals(2, storeBacked.discoveredCount(boss));
	}

	private static Mechanic mechanic(String id)
	{
		return new Mechanic(id, id, "desc", "counterplay", null, Collections.emptyList(), null, null);
	}

	private static class FakeDiscoveryStore implements DiscoveryStore
	{
		private final Set<String> data = new HashSet<>();
		private final List<String> writes = new ArrayList<>();

		@Override
		public Set<String> loadDiscovered()
		{
			return new HashSet<>(data);
		}

		@Override
		public void addDiscovered(String bossId, String mechanicId)
		{
			// Deliberately not re-spelling the format: an independent copy here would
			// keep agreeing with a drifted DiscoveryState and hide the breakage.
			writes.add(DiscoveryState.key(bossId, mechanicId));
			data.add(DiscoveryState.key(bossId, mechanicId));
		}
	}
}

package com.bossmechanics.detection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}

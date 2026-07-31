package com.bossmechanics.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.Mechanic;
import com.bossmechanics.data.Preview;
import com.bossmechanics.data.Trigger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class DetectionEngineTest
{
	// Sire-like fixture: npc-spawn triggers 5891/5908 are also presence-marking Sire forms
	// (docs/DECISIONS.md D17 ordering note).
	private static final int SIRE_AWAKE = 5886;
	private static final int SIRE_MINION_SURGE_FORM = 5891;
	private static final int SIRE_APOCALYPSE_FORM = 5908;
	private static final int TENTACLE_GUARD_NPC = 5912;
	private static final int MIASMA_ANIMATION = 4531;

	private static final Boss SIRE = sireFixture();

	private DiscoveryState state;
	private DetectionEngine engine;

	@Before
	public void setUp()
	{
		state = new DiscoveryState();
		engine = new DetectionEngine(Collections.singletonList(SIRE), state);
	}

	@Test
	public void animationBeforeAnySpawnIsEmpty()
	{
		assertTrue(engine.animationPlayed(1, MIASMA_ANIMATION).isEmpty());
	}

	@Test
	public void animationFromTrackedSireDiscoversMiasmaPools()
	{
		engine.npcSpawned(1, SIRE_AWAKE);

		List<Discovery> result = engine.animationPlayed(1, MIASMA_ANIMATION);

		assertEquals(1, result.size());
		assertEquals("miasma-pools", result.get(0).getMechanic().getId());
	}

	@Test
	public void animationFromUntrackedIndexIsEmpty()
	{
		engine.npcSpawned(1, SIRE_AWAKE);

		assertTrue(engine.animationPlayed(2, MIASMA_ANIMATION).isEmpty());
	}

	@Test
	public void repeatedAnimationIsEmptyAfterFirstDiscovery()
	{
		engine.npcSpawned(1, SIRE_AWAKE);
		engine.animationPlayed(1, MIASMA_ANIMATION);

		assertTrue(engine.animationPlayed(1, MIASMA_ANIMATION).isEmpty());
	}

	@Test
	public void npcSpawnTriggerRequiresBossPresence()
	{
		assertTrue(engine.npcSpawned(2, TENTACLE_GUARD_NPC).isEmpty());
	}

	@Test
	public void npcSpawnTriggerDiscoversWhenBossPresent()
	{
		engine.npcSpawned(1, SIRE_AWAKE);

		List<Discovery> result = engine.npcSpawned(2, TENTACLE_GUARD_NPC);

		assertEquals(1, result.size());
		assertEquals("tentacle-guard", result.get(0).getMechanic().getId());
	}

	@Test
	public void npcChangedDiscoversTriggerAndKeepsPresence()
	{
		engine.npcSpawned(1, SIRE_AWAKE);

		List<Discovery> result = engine.npcChanged(1, SIRE_APOCALYPSE_FORM);

		assertEquals(1, result.size());
		assertEquals("apocalypse", result.get(0).getMechanic().getId());

		// Presence survived the transform: this index still resolves to the Sire.
		List<Discovery> stillTracked = engine.animationPlayed(1, MIASMA_ANIMATION);
		assertEquals(1, stillTracked.size());
		assertEquals("miasma-pools", stillTracked.get(0).getMechanic().getId());
	}

	@Test
	public void coldNpcSpawnOfPresenceMarkingTriggerDiscoversInSameCall()
	{
		// SIRE_APOCALYPSE_FORM is simultaneously a presence marker and a trigger id
		// (docs/DECISIONS.md D17): presence must update before matching runs.
		List<Discovery> result = engine.npcSpawned(1, SIRE_APOCALYPSE_FORM);

		assertEquals(1, result.size());
		assertEquals("apocalypse", result.get(0).getMechanic().getId());
	}

	private static Boss sireFixture()
	{
		Mechanic miasmaPools = mechanic("miasma-pools", "Miasma Pools",
			Collections.singletonList(trigger("animation", MIASMA_ANIMATION)));
		Mechanic tentacleGuard = mechanic("tentacle-guard", "Tentacle Guard",
			Collections.singletonList(trigger("npc-spawn", TENTACLE_GUARD_NPC)));
		Mechanic minionSurge = mechanic("minion-surge", "Minion Surge",
			Collections.singletonList(trigger("npc-spawn", SIRE_MINION_SURGE_FORM)));
		Mechanic apocalypse = mechanic("apocalypse", "Apocalypse",
			Collections.singletonList(trigger("npc-spawn", SIRE_APOCALYPSE_FORM)));

		return new Boss("abyssal-sire", "Abyssal Sire",
			Arrays.asList(SIRE_AWAKE, SIRE_MINION_SURGE_FORM, SIRE_APOCALYPSE_FORM),
			"https://oldschool.runescape.wiki/w/Abyssal_Sire/Strategies",
			Arrays.asList(miasmaPools, tentacleGuard, minionSurge, apocalypse));
	}

	static Mechanic mechanic(String id, String name, List<Trigger> detection)
	{
		return new Mechanic(id, name, "description", "counterplay", null, detection,
			new Preview(null, null, false), null);
	}

	static Trigger trigger(String type, int id)
	{
		return new Trigger(type, id);
	}
}

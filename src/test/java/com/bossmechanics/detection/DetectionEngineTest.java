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

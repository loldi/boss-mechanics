package com.bossmechanics.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.Mechanic;
import com.bossmechanics.data.Preview;
import com.bossmechanics.data.Trigger;
import com.bossmechanics.data.TriggerType;
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

	// Vorkath-shaped fixture: projectiles have a source actor, graphics don't.
	private static final int VORKATH_NPC = 8059;
	private static final int ACID_PROJECTILE = 1483;
	private static final int SPAWN_GRAPHIC = 700;

	private static final Boss VORKATH = vorkathFixture();

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

	@Test
	public void npcDespawnedStopsFutureMatchingForThatIndex()
	{
		engine.npcSpawned(1, SIRE_AWAKE);

		engine.npcDespawned(1);

		assertTrue(engine.animationPlayed(1, MIASMA_ANIMATION).isEmpty());
	}

	@Test
	public void clearPresenceStopsMatchingButKeepsDiscoveryState()
	{
		engine.npcSpawned(1, SIRE_AWAKE);
		List<Discovery> firstWitness = engine.animationPlayed(1, MIASMA_ANIMATION);
		assertEquals(1, firstWitness.size());

		engine.clearPresence();

		assertTrue(engine.animationPlayed(1, MIASMA_ANIMATION).isEmpty());
		assertTrue(state.isDiscovered("abyssal-sire", "miasma-pools"));

		// Re-witnessing after presence is rebuilt stays empty: already discovered.
		engine.npcSpawned(1, SIRE_AWAKE);
		assertTrue(engine.animationPlayed(1, MIASMA_ANIMATION).isEmpty());
	}

	@Test
	public void projectileFromTrackedBossDiscoversAcidPoolBarrage()
	{
		DetectionEngine vorkathEngine = new DetectionEngine(Collections.singletonList(VORKATH), new DiscoveryState());
		vorkathEngine.npcSpawned(3, VORKATH_NPC);

		List<Discovery> result = vorkathEngine.projectileFired(3, ACID_PROJECTILE);

		assertEquals(1, result.size());
		assertEquals("acid-pool-barrage", result.get(0).getMechanic().getId());
	}

	@Test
	public void projectileFromUntrackedSourceIsEmptyEvenWithBossPresent()
	{
		DetectionEngine vorkathEngine = new DetectionEngine(Collections.singletonList(VORKATH), new DiscoveryState());
		vorkathEngine.npcSpawned(3, VORKATH_NPC);

		assertTrue(vorkathEngine.projectileFired(99, ACID_PROJECTILE).isEmpty());
	}

	@Test
	public void projectileWithNullSourceFallsBackToBossPresence()
	{
		DetectionEngine vorkathEngine = new DetectionEngine(Collections.singletonList(VORKATH), new DiscoveryState());

		assertTrue(vorkathEngine.projectileFired(null, ACID_PROJECTILE).isEmpty());

		vorkathEngine.npcSpawned(3, VORKATH_NPC);

		List<Discovery> result = vorkathEngine.projectileFired(null, ACID_PROJECTILE);
		assertEquals(1, result.size());
		assertEquals("acid-pool-barrage", result.get(0).getMechanic().getId());
	}

	@Test
	public void graphicGatesOnBossPresenceOnly()
	{
		DetectionEngine vorkathEngine = new DetectionEngine(Collections.singletonList(VORKATH), new DiscoveryState());

		assertTrue(vorkathEngine.graphicCreated(SPAWN_GRAPHIC).isEmpty());

		vorkathEngine.npcSpawned(3, VORKATH_NPC);

		List<Discovery> result = vorkathEngine.graphicCreated(SPAWN_GRAPHIC);
		assertEquals(1, result.size());
		assertEquals("zombified-spawn", result.get(0).getMechanic().getId());
	}

	@Test
	public void isKnownTriggerSeparatesUncuratedIdsFromAlreadyDiscoveredOnes()
	{
		// Both cases yield no discoveries, so the curation log needs this to tell them
		// apart: only a genuinely uncurated id is worth reporting.
		engine.npcSpawned(1, SIRE_AWAKE);
		engine.animationPlayed(1, MIASMA_ANIMATION);

		assertTrue(engine.animationPlayed(1, MIASMA_ANIMATION).isEmpty());
		assertTrue(engine.isKnownTrigger(TriggerType.ANIMATION, MIASMA_ANIMATION));
		assertTrue(!engine.isKnownTrigger(TriggerType.ANIMATION, 9999));
	}

	@Test
	public void claimedByNamesEveryMechanicSharingATriggerId()
	{
		// Two mechanics on one id is the case curation must be able to see: it means one
		// of them is labelled as the wrong move.
		Mechanic first = mechanic("first", "First",
			Collections.singletonList(trigger("animation", MIASMA_ANIMATION)));
		Mechanic second = mechanic("second", "Second",
			Collections.singletonList(trigger("animation", MIASMA_ANIMATION)));
		Boss shared = new Boss("shared", "Shared", Collections.singletonList(SIRE_AWAKE),
			"https://oldschool.runescape.wiki/w/Abyssal_Sire/Strategies",
			Arrays.asList(first, second));

		DetectionEngine sharedEngine = new DetectionEngine(
			Collections.singletonList(shared), state);

		String claimants = sharedEngine.claimedBy(TriggerType.ANIMATION, MIASMA_ANIMATION);
		assertTrue(claimants.contains("first"));
		assertTrue(claimants.contains("second"));
		assertEquals(null, sharedEngine.claimedBy(TriggerType.ANIMATION, 9999));
	}

	@Test
	public void trackedBossIdReportsPresenceAndClearsOnDespawn()
	{
		assertEquals(null, engine.trackedBossId(1));

		engine.npcSpawned(1, SIRE_AWAKE);
		assertEquals("abyssal-sire", engine.trackedBossId(1));

		engine.npcDespawned(1);
		assertEquals(null, engine.trackedBossId(1));
	}

	@Test
	public void animationDoesNotUnlockAnotherBossSharingTheSameAnimationId()
	{
		// Two bosses whose mechanics share one animation id. Only the boss actually
		// on screen may unlock; otherwise every boss learns a mechanic at once.
		Mechanic sharedIdMechanic = mechanic("shared-id-mechanic", "Shared Id Mechanic",
			Collections.singletonList(trigger("animation", MIASMA_ANIMATION)));
		Boss otherBoss = new Boss("other-boss", "Other Boss",
			Collections.singletonList(VORKATH_NPC),
			"https://oldschool.runescape.wiki/w/Vorkath/Strategies",
			Collections.singletonList(sharedIdMechanic));

		DetectionEngine bothBosses = new DetectionEngine(Arrays.asList(SIRE, otherBoss), state);
		bothBosses.npcSpawned(1, SIRE_AWAKE);

		List<Discovery> result = bothBosses.animationPlayed(1, MIASMA_ANIMATION);

		assertEquals(1, result.size());
		assertEquals("abyssal-sire", result.get(0).getBoss().getId());
		assertTrue(state.isDiscovered("abyssal-sire", "miasma-pools"));
		assertTrue(!state.isDiscovered("other-boss", "shared-id-mechanic"));
	}

	@Test
	public void repeatedProjectileOfSameFlightDiscoversOnlyOnce()
	{
		// ProjectileMoved fires every cycle of a projectile's flight, so the second
		// call onward must be silent or a single attack spams the chatbox.
		DetectionEngine vorkathEngine = new DetectionEngine(
			Collections.singletonList(VORKATH), state);
		vorkathEngine.npcSpawned(1, VORKATH_NPC);

		assertEquals(1, vorkathEngine.projectileFired(1, ACID_PROJECTILE).size());
		assertTrue(vorkathEngine.projectileFired(1, ACID_PROJECTILE).isEmpty());
		assertTrue(vorkathEngine.projectileFired(1, ACID_PROJECTILE).isEmpty());
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

	private static Boss vorkathFixture()
	{
		Mechanic acidPoolBarrage = mechanic("acid-pool-barrage", "Acid Pool Barrage",
			Collections.singletonList(trigger("projectile", ACID_PROJECTILE)));
		Mechanic zombifiedSpawn = mechanic("zombified-spawn", "Zombified Spawn",
			Collections.singletonList(trigger("graphic", SPAWN_GRAPHIC)));

		return new Boss("vorkath", "Vorkath", Collections.singletonList(VORKATH_NPC),
			"https://oldschool.runescape.wiki/w/Vorkath/Strategies",
			Arrays.asList(acidPoolBarrage, zombifiedSpawn));
	}

	static Mechanic mechanic(String id, String name, List<Trigger> detection)
	{
		return new Mechanic(id, name, "description", "counterplay", null, detection,
			new Preview(null, null, false, null, null), null);
	}

	static Trigger trigger(String type, int id)
	{
		return new Trigger(type, id);
	}
}

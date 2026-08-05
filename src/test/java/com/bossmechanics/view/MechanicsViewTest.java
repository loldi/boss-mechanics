package com.bossmechanics.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.BossDataLoader;
import com.bossmechanics.data.Mechanic;
import com.bossmechanics.data.Requirement;
import com.bossmechanics.detection.DiscoveryState;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The view model is where the reveal-vs-discovered invariant (docs/DECISIONS.md D10) actually
 * lives, so this is where it is pinned down. Nothing under {@code com.bossmechanics.ui} counts
 * anything; it renders what these methods return.
 */
public class MechanicsViewTest
{
	@Test
	public void rowsFollowSchemaOrderAndCoverEveryMechanic()
	{
		Boss sire = bundled("abyssal-sire");
		MechanicsView view = MechanicsView.of(sire, new DiscoveryState(), false);

		List<String> expectedIds = new ArrayList<>();
		for (Mechanic mechanic : sire.getMechanics())
		{
			expectedIds.add(mechanic.getId());
		}

		List<String> actualIds = new ArrayList<>();
		for (MechanicRow row : view.getRows())
		{
			actualIds.add(row.getMechanicId());
		}

		assertEquals(9, actualIds.size());
		assertEquals(expectedIds, actualIds);
	}

	@Test
	public void undiscoveredRowReadsQuestionMarks()
	{
		MechanicsView view = MechanicsView.of(testBoss(), new DiscoveryState(), false);
		MechanicRow row = view.getRows().get(0);

		assertTrue(row.isLocked());
		assertFalse(row.isDiscovered());
		assertEquals("???", row.getName());
		assertEquals("", row.getDescription());
		assertEquals("", row.getCounterplay());
		// FORK, resolved: a locked row shows no phase tag at all, so the reveal delivers something.
		assertNull(row.getPhase());
	}

	@Test
	public void discoveredRowCarriesRealText()
	{
		DiscoveryState state = new DiscoveryState();
		state.markDiscovered("test-boss", "m2");

		MechanicsView view = MechanicsView.of(testBoss(), state, false);
		MechanicRow row = view.getRows().get(1);

		assertFalse(row.isLocked());
		assertTrue(row.isDiscovered());
		assertEquals("Name m2", row.getName());
		assertEquals("Description m2", row.getDescription());
		assertEquals("Counterplay m2", row.getCounterplay());
		assertEquals("Phase 2", row.getPhase());
	}

	/** Issue #6, docs/DECISIONS.md D23: the row copies the preview spec, it never computes one. */
	@Test
	public void lockedRowsPreviewIsHiddenAndUnlockedRowsPreviewMatchesTheMechanic()
	{
		Boss sire = bundled("abyssal-sire");

		MechanicsView lockedView = MechanicsView.of(sire, new DiscoveryState(), false);
		assertFalse(lockedView.getRows().get(0).getPreview().isVisible());

		DiscoveryState state = new DiscoveryState();
		state.markDiscovered(sire.getId(), sire.getMechanics().get(0).getId());
		MechanicsView view = MechanicsView.of(sire, state, false);
		MechanicRow row = view.getRows().get(0);
		Mechanic mechanic = sire.getMechanics().get(0);

		assertEquals(PreviewSpec.of(mechanic, sire.getNpcIds(), false), row.getPreview());
	}

	@Test
	public void titleNamesTheBoss()
	{
		assertEquals("Boss Mechanics - Test Boss",
			MechanicsView.of(testBoss(), new DiscoveryState(), false).title());
	}

	// --- docs/DECISIONS.md D10: revealed and discovered are two different things. ---

	@Test
	public void revealUnlocksTextButNotDiscovery()
	{
		MechanicsView view = MechanicsView.of(testBoss(), new DiscoveryState(), true);

		for (MechanicRow row : view.getRows())
		{
			assertFalse("reveal must unlock the text", row.isLocked());
			assertFalse("reveal must not fake a discovery", row.isDiscovered());
		}
		assertEquals(0, view.discoveredCount());
	}

	@Test
	public void progressCountsGenuineDiscoveriesInBothToggleStates()
	{
		Boss sire = bundled("abyssal-sire");
		DiscoveryState state = new DiscoveryState();
		state.markDiscovered("abyssal-sire", "miasma-pools");
		state.markDiscovered("abyssal-sire", "spawn-summon");
		state.markDiscovered("abyssal-sire", "apocalypse");

		assertEquals(3, MechanicsView.of(sire, state, false).discoveredCount());
		assertEquals(3, MechanicsView.of(sire, state, true).discoveredCount());
		assertEquals(9, MechanicsView.of(sire, state, true).totalCount());
	}

	/**
	 * Pins the view to {@link DiscoveryState#discoveredCount(Boss)} rather than re-implementing
	 * the count, so the two can never drift into disagreeing about the same boss.
	 */
	@Test
	public void discoveredCountAgreesWithDiscoveryState()
	{
		Boss sire = bundled("abyssal-sire");
		DiscoveryState state = new DiscoveryState();
		state.markDiscovered("abyssal-sire", "scions");
		state.markDiscovered("abyssal-sire", "melee-combo");
		// A stale id, as a store from an older schema would hold: counted by neither.
		state.markDiscovered("abyssal-sire", "removed-mechanic");

		assertEquals(state.discoveredCount(sire), MechanicsView.of(sire, state, false).discoveredCount());
		assertEquals(state.discoveredCount(sire), MechanicsView.of(sire, state, true).discoveredCount());
	}

	@Test
	public void progressLabelReadsMechanicsDiscoveredNOverM()
	{
		DiscoveryState state = new DiscoveryState();
		state.markDiscovered("test-boss", "m1");

		assertEquals("Mechanics Discovered: 1/3",
			MechanicsView.of(testBoss(), state, true).progressLabel());
	}

	@Test
	public void progressFillWidthIsZeroAtNoneAndTrackAtAll()
	{
		assertEquals(0, MechanicsView.of(testBoss(), new DiscoveryState(), true).progressFillWidth(100));

		DiscoveryState all = new DiscoveryState();
		all.markDiscovered("test-boss", "m1");
		all.markDiscovered("test-boss", "m2");
		all.markDiscovered("test-boss", "m3");

		assertEquals(100, MechanicsView.of(testBoss(), all, false).progressFillWidth(100));
	}

	@Test
	public void progressFillWidthNeverExceedsTrack()
	{
		DiscoveryState state = new DiscoveryState();
		state.markDiscovered("test-boss", "m1");
		state.markDiscovered("test-boss", "m2");

		MechanicsView view = MechanicsView.of(testBoss(), state, false);

		assertEquals(66, view.progressFillWidth(100));
		assertTrue(view.progressFillWidth(7) <= 7);
		// A zero-width or negative track must not produce a negative fill the client would reject.
		assertEquals(0, view.progressFillWidth(0));
		assertEquals(0, view.progressFillWidth(-5));
	}

	/** A boss with no mechanics must not divide by zero. */
	@Test
	public void emptyBossHasNoProgressAndNoFill()
	{
		Boss empty = new Boss("empty", "Empty", Collections.singletonList(1), "https://example.com",
			Collections.emptyList());
		MechanicsView view = MechanicsView.of(empty, new DiscoveryState(), false);

		assertEquals("Mechanics Discovered: 0/0", view.progressLabel());
		assertEquals(0, view.progressFillWidth(100));
	}

	/**
	 * docs/DECISIONS.md D38: reveal flows through {@code ProfileStateStore}/{@code MechanicsView}
	 * and never consults the detection engine, so a mechanic's {@code requires} gate has no say
	 * over "View All" -- pinned here so a future coupling between the two fails loudly.
	 */
	@Test
	public void revealedGatedMechanicIsNotLocked()
	{
		Requirement gate = new Requirement("varp", 4828, 8);
		Mechanic gated = new Mechanic("gated", "Name gated", "Description gated", "Counterplay gated",
			null, Collections.emptyList(), gate, null, null);
		Boss boss = new Boss("test-boss", "Test Boss", Collections.singletonList(1),
			"https://example.com", Collections.singletonList(gated));

		MechanicsView view = MechanicsView.of(boss, new DiscoveryState(), true);
		MechanicRow row = view.getRows().get(0);

		assertFalse("View All must reveal a gated mechanic's text same as any other row", row.isLocked());
		assertFalse("Reveal must never fake a discovery", row.isDiscovered());
	}

	@Test
	public void revealActionLabelOffersTheOppositeOfTheCurrentState()
	{
		assertEquals("View All", MechanicsView.of(testBoss(), new DiscoveryState(), false).revealActionLabel());
		assertEquals("Hide All", MechanicsView.of(testBoss(), new DiscoveryState(), true).revealActionLabel());
	}

	/** The 3-mechanic fixture: m1 has no phase, m2 and m3 do. */
	private static Boss testBoss()
	{
		return new Boss("test-boss", "Test Boss", Collections.singletonList(1), "https://example.com",
			Arrays.asList(mechanic("m1", null), mechanic("m2", "Phase 2"), mechanic("m3", "Phase 3")));
	}

	private static Mechanic mechanic(String id, String phase)
	{
		return new Mechanic(id, "Name " + id, "Description " + id, "Counterplay " + id, phase,
			Collections.emptyList(), null, null, null);
	}

	/** The real bundled file, so schema order is asserted against curated data and not a fixture. */
	private static Boss bundled(String id)
	{
		for (Boss boss : new BossDataLoader(new Gson()).loadAll().getBosses())
		{
			if (boss.getId().equals(id))
			{
				return boss;
			}
		}
		throw new AssertionError("no bundled boss with id " + id);
	}
}

package com.bossmechanics.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.BossDataLoader;
import com.bossmechanics.data.Mechanic;
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

	@Test
	public void titleNamesTheBoss()
	{
		assertEquals("Boss Mechanics - Test Boss",
			MechanicsView.of(testBoss(), new DiscoveryState(), false).title());
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
			Collections.emptyList(), null, null);
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

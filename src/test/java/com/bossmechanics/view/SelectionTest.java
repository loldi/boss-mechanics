package com.bossmechanics.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.Mechanic;
import com.bossmechanics.detection.DiscoveryState;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/**
 * Which row the right-hand column is showing. The window rebuilds itself wholesale on every
 * "View All" flip, so "keep what was selected, unless it is gone" is a real rule with real edge
 * cases, and it should not be discovered by clicking around in game.
 */
public class SelectionTest
{
	@Test
	public void withNothingSelectedYetTakesTheFirstRow()
	{
		assertEquals("m1", Selection.resolve(view(), null, null));
	}

	@Test
	public void keepsASelectionThatIsStillOnTheList()
	{
		// The "View All" flip rebuilds every row, so the selection has to survive by id.
		assertEquals("m2", Selection.resolve(view(), "test-boss", "m2"));
	}

	@Test
	public void fallsBackToTheFirstRowWhenTheSelectedIdIsGone()
	{
		// A mechanic id renamed in a data update: D18 already says stale ids are never migrated.
		assertEquals("m1", Selection.resolve(view(), "test-boss", "removed-mechanic"));
	}

	@Test
	public void startsOverWhenTheBossChanged()
	{
		// Two bosses could share a mechanic id, and carrying one boss's selection into another's
		// screen would silently show a row the player never picked.
		assertEquals("m1", Selection.resolve(view(), "other-boss", "m2"));
	}

	@Test
	public void selectsNothingWhenThereAreNoRows()
	{
		assertNull(Selection.resolve(emptyView(), null, null));
		assertNull(Selection.resolve(emptyView(), "empty-boss", "m1"));
	}

	@Test
	public void rowForFindsTheRowAndAdmitsWhenItCannot()
	{
		MechanicsView view = view();

		assertEquals(view.getRows().get(1), Selection.rowFor(view, "m2"));
		assertNull(Selection.rowFor(view, "removed-mechanic"));
		assertNull(Selection.rowFor(view, null));
	}

	private static MechanicsView view()
	{
		Boss boss = new Boss("test-boss", "Test Boss", Collections.singletonList(1), "https://example.com",
			Arrays.asList(mechanic("m1"), mechanic("m2"), mechanic("m3")));
		return MechanicsView.of(boss, new DiscoveryState(), false);
	}

	private static MechanicsView emptyView()
	{
		Boss boss = new Boss("empty-boss", "Empty Boss", Collections.singletonList(1),
			"https://example.com", Collections.emptyList());
		return MechanicsView.of(boss, new DiscoveryState(), false);
	}

	private static Mechanic mechanic(String id)
	{
		return new Mechanic(id, "Name " + id, "Description " + id, "Counterplay " + id, null,
			Collections.emptyList(), null, null, null);
	}
}

package com.bossmechanics.view;

/**
 * Which mechanic the window's right-hand column is showing, resolved the same way every time.
 *
 * <p>The window rebuilds itself wholesale whenever "View All" flips, which throws every row widget
 * away and builds new ones. The selection therefore has to survive as an id rather than as a
 * reference, and "the id I had is no longer on the list" has to mean something specific. It means
 * the first row, so the right-hand column is never blank while rows exist — which is the
 * acceptance criterion that says the panel never reflows.
 *
 * <p>Lives in {@code com.bossmechanics.view}, the RuneLite-free package, for the same reason
 * {@link MechanicsView} does: it is a decision, and decisions get unit tests. {@code MechanicsView}
 * itself is untouched (and so are its 12 tests) because selection is the window's state, not the
 * view model's — two windows on two bosses would disagree about it.
 */
public final class Selection
{
	private Selection()
	{
	}

	/**
	 * @param previousBossId the boss the previous selection belonged to, or null if there was none
	 * @param previousMechanicId what was selected then, or null
	 * @return the mechanic id to select, or null if there is nothing to select
	 */
	public static String resolve(MechanicsView view, String previousBossId, String previousMechanicId)
	{
		if (view.getRows().isEmpty())
		{
			return null;
		}

		// Mechanic ids are only unique within a boss, so a carried-over id from another boss could
		// match by accident and show a row nobody picked.
		if (view.getBossId().equals(previousBossId) && rowFor(view, previousMechanicId) != null)
		{
			return previousMechanicId;
		}

		return view.getRows().get(0).getMechanicId();
	}

	/** @return the row with this id, or null if there isn't one (including for a null id). */
	public static MechanicRow rowFor(MechanicsView view, String mechanicId)
	{
		if (mechanicId == null)
		{
			return null;
		}

		for (MechanicRow row : view.getRows())
		{
			if (mechanicId.equals(row.getMechanicId()))
			{
				return row;
			}
		}
		return null;
	}
}

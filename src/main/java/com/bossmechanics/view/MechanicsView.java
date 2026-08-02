package com.bossmechanics.view;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.Mechanic;
import com.bossmechanics.detection.DiscoveryState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * Everything the Boss Mechanics window prints, resolved once, off the client thread's critical
 * path and away from any {@code Widget}. The third RuneLite-free package: {@code data} and
 * {@code detection} carry the rest of the project's tests for the same reason.
 *
 * <p>This is where the reveal-vs-discovered invariant (docs/DECISIONS.md D10, D20) lives.
 * {@link #discoveredCount()} counts genuine discoveries; revealing only clears {@code locked}.
 * The interface counts nothing, so it cannot get this wrong.
 */
@Value
public class MechanicsView
{
	private static final String LOCKED_NAME = "???";

	String bossId;
	String bossName;
	boolean revealed;
	/** Schema order, always every mechanic. Locked ones are present as "???" rows (D12). */
	List<MechanicRow> rows;

	public static MechanicsView of(Boss boss, DiscoveryState state, boolean revealed)
	{
		List<MechanicRow> rows = new ArrayList<>();
		for (Mechanic mechanic : boss.getMechanics())
		{
			boolean discovered = state.isDiscovered(boss.getId(), mechanic.getId());
			boolean locked = !discovered && !revealed;

			rows.add(new MechanicRow(
				mechanic.getId(),
				discovered,
				locked,
				locked ? LOCKED_NAME : mechanic.getName(),
				locked ? "" : mechanic.getDescription(),
				locked ? "" : mechanic.getCounterplay(),
				// FORK, resolved: a locked row shows no phase tag, so revealing delivers something.
				locked ? null : mechanic.getPhase(),
				PreviewSpec.of(mechanic, boss.getNpcIds(), locked)));
		}

		return new MechanicsView(boss.getId(), boss.getName(), revealed, Collections.unmodifiableList(rows));
	}

	public String title()
	{
		return "Boss Mechanics - " + bossName;
	}

	/**
	 * Genuine discoveries only. Counts rows where {@code discovered}, never rows where
	 * {@code !locked}, which is the whole of D10: revealing changes what you can read and
	 * nothing about what you have earned.
	 */
	public int discoveredCount()
	{
		int count = 0;
		for (MechanicRow row : rows)
		{
			if (row.isDiscovered())
			{
				count++;
			}
		}
		return count;
	}

	public int totalCount()
	{
		return rows.size();
	}

	public String progressLabel()
	{
		return "Mechanics Discovered: " + discoveredCount() + "/" + totalCount();
	}

	public String revealActionLabel()
	{
		return revealed ? "Hide All" : "View All";
	}

	/**
	 * How many pixels of a {@code trackWidth}-wide bar are filled, the same
	 * {@code done * width / total} scale the Combat Achievements bar uses (script 4782).
	 * Clamped at both ends so a zero-mechanic boss or a track too narrow to have been laid
	 * out yet can never hand the client a negative width.
	 */
	public int progressFillWidth(int trackWidth)
	{
		if (trackWidth <= 0 || totalCount() == 0)
		{
			return 0;
		}

		int fill = discoveredCount() * trackWidth / totalCount();
		return Math.max(0, Math.min(trackWidth, fill));
	}
}

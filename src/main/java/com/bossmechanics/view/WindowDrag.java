package com.bossmechanics.view;

/**
 * The clamp math behind the draggable window (docs/DECISIONS.md D29, unanchored by D32).
 * RuneLite-free, like every other decision in this package: {@code ui.BossMechanicsWindow} owns
 * the window's absolute origin as state; this only answers "given this origin, where does the
 * window actually land so it stays on screen."
 *
 * <p>Static, stateless, no RuneLite import.
 */
public final class WindowDrag
{
	private WindowDrag()
	{
	}

	/**
	 * Clamps an absolute origin so the window stays fully on the host when it fits, and pinned to
	 * the host's own span when it does not.
	 *
	 * <p>Bounds are {@code [min(0, hostSize - windowSize), max(0, hostSize - windowSize)]}. When
	 * the window fits ({@code hostSize >= windowSize}) that pair is {@code [<=0, >=0]}, an ordinary
	 * clamp keeping the window fully on screen. When it does not fit, the pair inverts to
	 * {@code [hostSize - windowSize, 0]}, both non-positive, and the window pins to the host's own
	 * origin edge rather than the clamp throwing or doing nothing.
	 *
	 * <p>Replaces D29's {@code clampedOrigin}, which existed to never clamp tighter than a
	 * "computed origin" re-derived from the collection log every tick. Under D32's absolute
	 * position model there is no such reference to protect: the window's origin is session state,
	 * seeded once and then dragged, so the only question left is whether it currently fits.
	 *
	 * @param origin the window's own stored (or candidate) origin on this axis, in host coordinates
	 * @param windowSize the window's own logical size on this axis (512 or 334)
	 * @param hostSize the host's current size on this axis
	 * @return the origin to actually draw the window at
	 */
	public static int visibleOrigin(int origin, int windowSize, int hostSize)
	{
		int min = Math.min(0, hostSize - windowSize);
		int max = Math.max(0, hostSize - windowSize);
		return Math.max(min, Math.min(max, origin));
	}
}

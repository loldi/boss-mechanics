package com.bossmechanics.view;

/**
 * The clamp math behind issue #48's draggable window (docs/DECISIONS.md D29). RuneLite-free, like
 * every other decision in this package: the window's dragged offset is state, but where that
 * offset is allowed to land is a pure decision, resolved one axis at a time the same way
 * {@code WindowPlacement.origin} already does.
 *
 * <p>Static, stateless, no RuneLite import — {@code ui.BossMechanicsWindow} owns the drag state
 * machine and the offset fields; this only answers "given this offset, where does the window's
 * origin actually land."
 */
public final class WindowDrag
{
	private WindowDrag()
	{
	}

	/**
	 * Clamps a dragged window origin so the window never goes fully off the host, without ever
	 * clamping tighter than {@code computedOrigin} itself.
	 *
	 * <p>Bounds are {@code [min(0, computedOrigin), max(hostSize - windowSize, computedOrigin)]}. A
	 * zero offset therefore always reproduces {@code computedOrigin} exactly — including a
	 * legitimately negative one (D20's overhang) — because both bounds already include it before
	 * the offset is even added. An offset can only ever move the origin back toward on-screen, never
	 * push an already-out-of-bounds origin further out.
	 *
	 * @param computedOrigin where {@code WindowPlacement.origin} put the window before any drag
	 * @param offset the player's accumulated drag offset on this axis
	 * @param windowSize the window's own logical size on this axis (512 or 334)
	 * @param hostSize the host's current size on this axis
	 * @return the origin to actually place the window at
	 */
	public static int clampedOrigin(int computedOrigin, int offset, int windowSize, int hostSize)
	{
		int min = Math.min(0, computedOrigin);
		int max = Math.max(hostSize - windowSize, computedOrigin);
		return Math.max(min, Math.min(max, computedOrigin + offset));
	}
}

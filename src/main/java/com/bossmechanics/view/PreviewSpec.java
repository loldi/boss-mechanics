package com.bossmechanics.view;

import com.bossmechanics.data.Mechanic;
import com.bossmechanics.data.Preview;
import java.util.List;
import lombok.Value;

/**
 * What the model box renders for one mechanic (issue #6, docs/DECISIONS.md D23): which npc's
 * model to show, which animation to loop (or none, for a static pose), at what zoom, and whether
 * to show anything at all. Resolved once here so {@code com.bossmechanics.ui} never decides what
 * a locked row or a {@code staticFallback} mechanic looks like; it only mutates a widget to match.
 */
@Value
public class PreviewSpec
{
	/** No animation: the model box shows a static pose instead of playing anything. */
	public static final int NO_ANIMATION = -1;

	/** Frames a large boss well; validated by the issue #1 spike (docs/DECISIONS.md D14). */
	public static final int DEFAULT_ZOOM = 3000;

	/** false for a locked row: nothing renders, so a locked mechanic can never leak via the model. */
	boolean visible;
	int npcId;
	int animationId;
	int zoom;
	/**
	 * Vertical anchor correction pixels, resolved from {@link Preview#getShiftY()} (docs/
	 * DECISIONS.md D26); 0 means no correction. {@code com.bossmechanics.ui} turns this into a
	 * pool widget rect mutation, never a decision of its own.
	 */
	int shiftY;
	/**
	 * Explicit cache model id (docs/DECISIONS.md D27), resolved from {@link Preview#getModelId()};
	 * null means "resolve the model from {@link #npcId} instead" (the existing behaviour).
	 * {@code com.bossmechanics.ui} must use this directly, skipping its npc -> model lookup
	 * entirely, when present.
	 */
	Integer modelId;

	/**
	 * FORK, resolved (Option A, docs/DECISIONS.md D23): a static pose always wins over
	 * {@code animationId} when {@code staticFallback} is true, and a missing {@code animationId}
	 * always falls back to a static pose rather than crashing, even when {@code staticFallback}
	 * is false or the mechanic carries no {@code preview} at all.
	 *
	 * @param bossNpcIds the boss's own npcIds; {@code preview.npcId} wins when present, else
	 *     {@code bossNpcIds.get(0)}
	 * @param locked true for a "???" row; resolves to {@link #hidden()} regardless of preview data,
	 *     so a locked mechanic can never spoil itself through the model
	 */
	public static PreviewSpec of(Mechanic mechanic, List<Integer> bossNpcIds, boolean locked)
	{
		if (locked)
		{
			return hidden();
		}

		Preview preview = mechanic.getPreview();
		int npcId = preview != null && preview.getNpcId() != null ? preview.getNpcId() : bossNpcIds.get(0);
		int zoom = preview != null && preview.getZoom() != null ? preview.getZoom() : DEFAULT_ZOOM;
		int shiftY = preview != null && preview.getShiftY() != null ? preview.getShiftY() : 0;
		Integer modelId = preview == null ? null : preview.getModelId();

		boolean staticFallback = preview != null && preview.isStaticFallback();
		Integer animationId = preview == null ? null : preview.getAnimationId();
		int resolvedAnimationId = staticFallback || animationId == null ? NO_ANIMATION : animationId;

		return new PreviewSpec(true, npcId, resolvedAnimationId, zoom, shiftY, modelId);
	}

	public static PreviewSpec hidden()
	{
		return new PreviewSpec(false, 0, NO_ANIMATION, DEFAULT_ZOOM, 0, null);
	}
}

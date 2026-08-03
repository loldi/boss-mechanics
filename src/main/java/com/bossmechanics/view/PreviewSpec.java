package com.bossmechanics.view;

import com.bossmechanics.data.Mechanic;
import com.bossmechanics.data.Preview;
import com.bossmechanics.data.SecondaryPreview;
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
	 * Bundled sprite resource name (docs/DECISIONS.md D27), resolved from
	 * {@link Preview#getSprite()}; null means "no sprite, render a model as usual". Wins over
	 * every model field in the preference ladder: sprite &gt; modelId &gt; npcId+animationId &gt;
	 * idle &gt; raw pose. {@code com.bossmechanics.ui} shows the bundled image and skips model
	 * resolution entirely when this is present.
	 */
	String sprite;
	/**
	 * Horizontal anchor offset pixels, resolved from {@link Preview#getShiftX()} (docs/
	 * DECISIONS.md D27); 0 means no correction. Mirrors {@link #shiftY}'s mechanism sideways.
	 */
	int shiftX;
	/**
	 * A second model shown beside the primary one (docs/DECISIONS.md D27, shape (a)), resolved
	 * from {@link Preview#getSecondary()}; null means no secondary. {@code com.bossmechanics.ui}
	 * renders it exactly like the primary (its own modelId/npcId precedence, zoom, shiftX/shiftY),
	 * just in a second pool slot.
	 */
	SecondaryPreviewSpec secondary;

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

		// Sprite tier (docs/DECISIONS.md D27): wins over every model field, so none of them need
		// resolving at all when a sprite is curated.
		String sprite = preview == null ? null : preview.getSprite();
		if (sprite != null)
		{
			return new PreviewSpec(true, 0, NO_ANIMATION, DEFAULT_ZOOM, 0, null, sprite, 0, null);
		}

		int npcId = preview != null && preview.getNpcId() != null ? preview.getNpcId() : bossNpcIds.get(0);
		int zoom = preview != null && preview.getZoom() != null ? preview.getZoom() : DEFAULT_ZOOM;
		int shiftY = preview != null && preview.getShiftY() != null ? preview.getShiftY() : 0;
		int shiftX = preview != null && preview.getShiftX() != null ? preview.getShiftX() : 0;
		Integer modelId = preview == null ? null : preview.getModelId();

		boolean staticFallback = preview != null && preview.isStaticFallback();
		Integer animationId = preview == null ? null : preview.getAnimationId();
		int resolvedAnimationId = staticFallback || animationId == null ? NO_ANIMATION : animationId;

		SecondaryPreview secondaryData = preview == null ? null : preview.getSecondary();
		SecondaryPreviewSpec secondary = secondaryData == null ? null : SecondaryPreviewSpec.of(secondaryData);

		return new PreviewSpec(true, npcId, resolvedAnimationId, zoom, shiftY, modelId, null, shiftX, secondary);
	}

	public static PreviewSpec hidden()
	{
		return new PreviewSpec(false, 0, NO_ANIMATION, DEFAULT_ZOOM, 0, null, null, 0, null);
	}
}

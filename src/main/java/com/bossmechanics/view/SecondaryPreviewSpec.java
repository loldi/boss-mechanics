package com.bossmechanics.view;

import com.bossmechanics.data.SecondaryPreview;
import lombok.Value;

/**
 * A second model shown beside the primary preview (issue #6, docs/DECISIONS.md D27, secondary
 * models, shape (a)). Resolved once here, mirroring {@link PreviewSpec}'s modelId/npcId
 * precedence, so {@code com.bossmechanics.ui} only ever mutates a widget to match -- it never
 * decides whether a model comes from an explicit id or an npc lookup.
 */
@Value
public class SecondaryPreviewSpec
{
	/** Explicit cache model id; null means "resolve the model from {@link #npcId} instead". */
	Integer modelId;
	int npcId;
	int animationId;
	int zoom;
	int shiftX;
	int shiftY;
	/** Model rotation about its own X/Y/Z axes, 0-2047 per axis; mirrors {@link PreviewSpec}'s. */
	int rotationX;
	int rotationY;
	int rotationZ;

	static SecondaryPreviewSpec of(SecondaryPreview secondary)
	{
		int npcId = secondary.getNpcId() != null ? secondary.getNpcId() : 0;
		int animationId = secondary.getAnimationId() != null ? secondary.getAnimationId() : PreviewSpec.NO_ANIMATION;
		int zoom = secondary.getZoom() != null ? secondary.getZoom() : PreviewSpec.DEFAULT_ZOOM;
		int shiftX = secondary.getShiftX() != null ? secondary.getShiftX() : 0;
		int shiftY = secondary.getShiftY() != null ? secondary.getShiftY() : 0;
		int rotationX = secondary.getRotationX() != null ? secondary.getRotationX() : 0;
		int rotationY = secondary.getRotationY() != null ? secondary.getRotationY() : 0;
		int rotationZ = secondary.getRotationZ() != null ? secondary.getRotationZ() : 0;

		return new SecondaryPreviewSpec(secondary.getModelId(), npcId, animationId, zoom, shiftX, shiftY,
			rotationX, rotationY, rotationZ);
	}
}

package com.bossmechanics.data;

import lombok.Value;

/**
 * A second model shown beside the primary preview (see data/SCHEMA.md "Preview"), for the two
 * demonstrated cases: Miasma Pools (the Sire's body plus the pool's own model) and Scions (the
 * grown scion plus the spawn it matures from). Resolution mirrors {@link Preview}'s modelId/npcId
 * precedence (see {@code PreviewSpec.of}/{@code SecondaryPreviewSpec.of}), but lives in its own
 * type rather than reusing {@link Preview} wholesale: {@code staticFallback} and {@code sprite}
 * have no meaning for a secondary model.
 */
@Value
public class SecondaryPreview
{
	/** Explicit cache model id; null means "resolve from npcId instead" (mirrors Preview). */
	Integer modelId;
	Integer npcId;
	Integer animationId;
	Integer zoom;
	/** Horizontal anchor offset, in pixels; null means 0 (docs/DECISIONS.md D27). */
	Integer shiftX;
	/** Vertical anchor correction, in pixels; null means 0 (docs/DECISIONS.md D26/D27). */
	Integer shiftY;
}

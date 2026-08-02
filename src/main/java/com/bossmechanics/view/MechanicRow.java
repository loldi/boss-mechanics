package com.bossmechanics.view;

import lombok.Value;

/**
 * One already-resolved list row: every string is exactly what the interface prints, so nothing
 * under {@code com.bossmechanics.ui} decides what a locked mechanic looks like.
 *
 * <p>{@code discovered} and {@code locked} are separate fields on purpose (docs/DECISIONS.md D10).
 * {@code locked} is {@code !discovered && !revealed}, so revealing unlocks the text without
 * touching discovery, and the progress bar counts {@code discovered} either way.
 */
@Value
public class MechanicRow
{
	String mechanicId;
	boolean discovered;
	boolean locked;
	/** "???" when locked. */
	String name;
	/** "" when locked. */
	String description;
	/** "" when locked. */
	String counterplay;
	/** null when locked, and when the mechanic has no phase. */
	String phase;
	/** Never null; {@code hidden()} when locked (docs/DECISIONS.md D23, issue #6). */
	PreviewSpec preview;
}

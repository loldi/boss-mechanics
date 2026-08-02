package com.bossmechanics.view;

import java.util.function.ToIntFunction;

/**
 * Fits a string into a pixel budget, cutting it with a trailing "..." when it will not fit whole.
 *
 * <p>Exists because a list row's name and its right-aligned phase tag are two texts sharing one
 * 22px line: given both full row width, a long name ("Respiratory Systems") runs under the tag.
 * The row instead reserves the tag's zone and fits the name into what is left, using this.
 *
 * <p>Measurement is a {@code ToIntFunction<String>} rather than anything font-shaped so this
 * package keeps its zero-RuneLite property; the interface passes the widget font's own
 * {@code getTextWidth}, and the tests pass arithmetic.
 */
public final class Ellipsize
{
	private static final String ELLIPSIS = "...";

	private Ellipsize()
	{
	}

	/**
	 * @param text the string to fit; returned unchanged when it already fits
	 * @param widthOf pixel width of a candidate string, in whatever font the caller renders in
	 * @param maxWidth the pixel budget
	 * @return the longest prefix of {@code text} (plus "...") that fits; bare "..." if nothing does
	 */
	public static String fit(String text, ToIntFunction<String> widthOf, int maxWidth)
	{
		if (widthOf.applyAsInt(text) <= maxWidth)
		{
			return text;
		}

		for (int length = text.length() - 1; length > 0; length--)
		{
			String candidate = text.substring(0, length).trim() + ELLIPSIS;
			if (widthOf.applyAsInt(candidate) <= maxWidth)
			{
				return candidate;
			}
		}
		return ELLIPSIS;
	}
}

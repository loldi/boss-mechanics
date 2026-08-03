package com.bossmechanics.view;

import java.util.function.ToIntFunction;

/**
 * Greedy word-wraps a string and reports how many lines it takes, so {@code MechanicsDetail} can
 * stack the name/description/counterplay text blocks by their real wrapped height instead of a
 * fixed guess (docs/DECISIONS.md D28, issue #47 follow-up: the counterplay text clipped inside its
 * fixed-height box for long mechanics like "Tentacle Guard").
 *
 * <p>The client's own TEXT widget still does the actual line-wrapped rendering; this only needs to
 * agree with it closely enough to size and position widgets without them overlapping. Measurement
 * is a {@code ToIntFunction<String>} rather than anything font-shaped, the same seam
 * {@link Ellipsize} already uses, so this package keeps its zero-RuneLite property: the interface
 * passes the widget font's own {@code getTextWidth}, and the tests pass arithmetic.
 */
public final class LineWrap
{
	private LineWrap()
	{
	}

	/**
	 * @param text the string to wrap; null or blank counts as one (empty) line, matching how a
	 *     real text widget still occupies a line even with nothing in it
	 * @param widthOf pixel width of a candidate string, in whatever font the caller renders in
	 * @param maxWidth the pixel budget per line
	 * @return the number of lines greedy word-wrapping produces; always at least 1
	 */
	public static int lines(String text, ToIntFunction<String> widthOf, int maxWidth)
	{
		if (text == null || text.trim().isEmpty())
		{
			return 1;
		}

		String[] words = text.trim().split("\\s+");
		int lineCount = 1;
		String currentLine = "";

		for (String word : words)
		{
			String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;

			// A line is never left empty just because the very first word alone overruns the
			// budget -- there is nothing shorter to put there, so it stands as its own line.
			if (currentLine.isEmpty() || widthOf.applyAsInt(candidate) <= maxWidth)
			{
				currentLine = candidate;
			}
			else
			{
				lineCount++;
				currentLine = word;
			}
		}

		return lineCount;
	}
}

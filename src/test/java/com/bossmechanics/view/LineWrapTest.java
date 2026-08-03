package com.bossmechanics.view;

import static org.junit.Assert.assertEquals;

import java.util.function.ToIntFunction;
import org.junit.Test;

/**
 * The text scroll box (docs/DECISIONS.md D28, issue #47 follow-up) needs to know how many lines a
 * block of text will wrap to, so {@code MechanicsDetail} can stack name/description/counterplay
 * without overlapping and size the scrollable content to match. The width function here is 7px
 * per character, the same fixture {@link EllipsizeTest} uses.
 */
public class LineWrapTest
{
	private static final ToIntFunction<String> SEVEN_PER_CHAR = s -> s.length() * 7;

	@Test
	public void emptyTextIsOneLine()
	{
		assertEquals(1, LineWrap.lines("", SEVEN_PER_CHAR, 100));
	}

	@Test
	public void textThatFitsOnOneLineIsOneLine()
	{
		// "Kill it fast." is 14 chars = 98px, under a 100px budget.
		assertEquals(1, LineWrap.lines("Kill it fast.", SEVEN_PER_CHAR, 100));
	}

	@Test
	public void textWrapsWhenAWordWouldOverrunTheLine()
	{
		// "Stun the Sire to drop them" is 26 chars = 182px; a 100px budget fits "Stun the Sire"
		// (91px) but "to" would push past it, so "to drop them" starts a second line.
		assertEquals(2, LineWrap.lines("Stun the Sire to drop them", SEVEN_PER_CHAR, 100));
	}

	@Test
	public void everyWordThatOverflowsStartsAFreshLine()
	{
		// Four words, each just under budget alone but any two together overrun it: one word per
		// line, four lines.
		assertEquals(4, LineWrap.lines("Alpha Bravo Charlie Delta", SEVEN_PER_CHAR, 42));
	}

	@Test
	public void aSingleWordLongerThanTheBudgetStillCountsAsOneLine()
	{
		// A word that alone exceeds the budget can't be split further, so it still only takes one
		// line rather than looping forever or throwing.
		assertEquals(1, LineWrap.lines("Antidisestablishmentarianism", SEVEN_PER_CHAR, 10));
	}

	@Test
	public void multipleWhitespaceBetweenWordsIsCollapsed()
	{
		assertEquals(1, LineWrap.lines("Kill   it   fast.", SEVEN_PER_CHAR, 100));
	}

	@Test
	public void aRealCounterplaySentenceMatchesTheExpectedLineCount()
	{
		// The Tentacle Guard counterplay that motivated this class, wrapped across a 267px column.
		String counterplay = "Stun the Sire to drop them for 30 seconds. Shadow Barrage always "
			+ "stuns; 75 ranged or magic damage also works.";
		assertEquals(3, LineWrap.lines(counterplay, SEVEN_PER_CHAR, 267));
	}
}

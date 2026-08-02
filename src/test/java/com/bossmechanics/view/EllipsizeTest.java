package com.bossmechanics.view;

import static org.junit.Assert.assertEquals;

import java.util.function.ToIntFunction;
import org.junit.Test;

/**
 * The list row's name-fitting rule. The width function here is 7px per character, which is close
 * enough to the real BOLD_12 average that the "Respiratory Systems" case below mirrors the row
 * that actually overlapped in game.
 */
public class EllipsizeTest
{
	private static final ToIntFunction<String> SEVEN_PER_CHAR = s -> s.length() * 7;

	@Test
	public void aNameThatFitsIsUntouched()
	{
		assertEquals("Tentacle Guard", Ellipsize.fit("Tentacle Guard", SEVEN_PER_CHAR, 120));
	}

	@Test
	public void aNameExactlyAtTheBudgetIsUntouched()
	{
		// 14 chars * 7 = 98.
		assertEquals("Tentacle Guard", Ellipsize.fit("Tentacle Guard", SEVEN_PER_CHAR, 98));
	}

	@Test
	public void aLongNameIsCutWithAnEllipsisInsideTheBudget()
	{
		// "Respiratory Systems" is 19 chars = 133px; the budget fits 17 (119px), so the longest
		// prefix + "..." that fits is 14 chars + "...".
		String fitted = Ellipsize.fit("Respiratory Systems", SEVEN_PER_CHAR, 119);
		assertEquals("Respiratory Sy...", fitted);
	}

	@Test
	public void theCutNeverEndsInATrailingSpaceBeforeTheEllipsis()
	{
		// A cut landing right after "Respiratory " must trim to "Respiratory..." not
		// "Respiratory ...".
		assertEquals("Respiratory...", Ellipsize.fit("Respiratory Systems", SEVEN_PER_CHAR, 100));
	}

	@Test
	public void aBudgetTooSmallForAnythingDegradesToABareEllipsis()
	{
		assertEquals("...", Ellipsize.fit("Apocalypse", SEVEN_PER_CHAR, 10));
	}
}

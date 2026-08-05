package com.bossmechanics.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class BossPageIndexTest
{
	private static final Boss SIRE = boss("abyssal-sire", "Abyssal Sire");
	private static final Boss VORKATH = boss("vorkath", "Vorkath");

	private static final List<Boss> BOSSES = Arrays.asList(SIRE, VORKATH);

	@Test
	public void exactPageTitleFindsItsBoss()
	{
		assertSame(VORKATH, new BossPageIndex(BOSSES).forPageTitle("Vorkath"));
	}

	@Test
	public void titleMatchIgnoresCaseAndSurroundingWhitespace()
	{
		assertSame(VORKATH, new BossPageIndex(BOSSES).forPageTitle("  vorkath  "));
	}

	/** Collection log headers arrive with the game's colour markup still in them. */
	@Test
	public void titleMatchStripsColourTags()
	{
		assertSame(SIRE, new BossPageIndex(BOSSES).forPageTitle("<col=ff981f>Abyssal Sire</col>"));
	}

	/**
	 * The header widget can be missing or empty mid-rebuild, so the caller cannot promise
	 * a usable title. Answering null beats making every caller pre-check.
	 */
	@Test
	public void missingOrBlankTitleIsNotAMatch()
	{
		BossPageIndex index = new BossPageIndex(BOSSES);

		assertNull(index.forPageTitle(null));
		assertNull(index.forPageTitle(""));
		assertNull(index.forPageTitle("   "));
	}

	/** The "every other collection log page shows nothing" half of the acceptance criteria. */
	@Test
	public void aPageWeHaveNoDataForIsNotAMatch()
	{
		assertNull(new BossPageIndex(BOSSES).forPageTitle("Chambers of Xeric"));
		assertNull(new BossPageIndex(Collections.emptyList()).forPageTitle("Vorkath"));
	}

	/**
	 * What the curation log prints on a miss. It has to be the same string the lookup used,
	 * or it would send a curator off to write a name that still doesn't match.
	 */
	@Test
	public void pageTitleKeyIsTheStringTheDataMustMatch()
	{
		assertEquals("the mad angel", BossPageIndex.pageTitleKey("<col=ff981f>The Mad Angel</col>"));
		assertEquals("k'ril tsutsaroth", BossPageIndex.pageTitleKey("  K'ril   Tsutsaroth "));
	}

	@Test
	public void pageTitleKeyIsNullWhenThereIsNothingToMatch()
	{
		assertNull(BossPageIndex.pageTitleKey(null));
		assertNull(BossPageIndex.pageTitleKey("   "));
		assertNull(BossPageIndex.pageTitleKey("<col=ff981f></col>"));
	}

	private static Boss boss(String id, String name)
	{
		return new Boss(id, name, Collections.singletonList(1), "https://example.com",
			Collections.emptyList());
	}
}

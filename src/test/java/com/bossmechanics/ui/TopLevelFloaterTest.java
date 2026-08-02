package com.bossmechanics.ui;

import static org.junit.Assert.assertEquals;

import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

/**
 * The one testable thing in {@code com.bossmechanics.ui}: a pure lookup with no live widget
 * tree behind it. A missing layout here means the window silently never opens, which is exactly
 * the failure mode worth catching offline.
 */
public class TopLevelFloaterTest
{
	@Test
	public void everyTopLevelLayoutMapsToItsFloater()
	{
		assertEquals(InterfaceID.ToplevelSpectator.FLOATER,
			TopLevelFloater.componentId(InterfaceID.TOPLEVEL_SPECTATOR));
		assertEquals(InterfaceID.ToplevelOsrsStretch.FLOATER,
			TopLevelFloater.componentId(InterfaceID.TOPLEVEL_OSRS_STRETCH));
		assertEquals(InterfaceID.ToplevelPreEoc.FLOATER,
			TopLevelFloater.componentId(InterfaceID.TOPLEVEL_PRE_EOC));
		assertEquals(InterfaceID.ToplevelDisplay.FLOATER,
			TopLevelFloater.componentId(InterfaceID.TOPLEVEL_DISPLAY));
		assertEquals(InterfaceID.Toplevel.FLOATER,
			TopLevelFloater.componentId(InterfaceID.TOPLEVEL));
		assertEquals(InterfaceID.ToplevelOsm.FLOATER,
			TopLevelFloater.componentId(InterfaceID.TOPLEVEL_OSM));
	}

	@Test
	public void unknownLayoutsReportMinusOne()
	{
		// -1 is what Client.getTopLevelInterfaceId returns before any layout is loaded, and
		// passing it to getWidget indexes the client's group array with -1 and throws.
		assertEquals(-1, TopLevelFloater.componentId(-1));
		assertEquals(-1, TopLevelFloater.componentId(9999));
		assertEquals(-1, TopLevelFloater.componentId(InterfaceID.COLLECTION));
	}
}

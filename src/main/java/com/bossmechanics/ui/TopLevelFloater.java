package com.bossmechanics.ui;

import net.runelite.api.gameval.InterfaceID;

/**
 * Which component hosts our window, for whichever top-level layout the client is currently
 * running (fixed, resizable-stretch, pre-EoC, display, mobile, spectator).
 *
 * <p>FLOATER, not MAINMODAL (docs/DECISIONS.md D20). In all six layouts FLOATER is a later
 * sibling of MAINMODAL under the same parent, so it draws on top of the collection log, and it
 * is {@code w=0 h=0} with both size modes MINUS, so it always resolves to the full parent size
 * and cannot clip a child to nothing. It is also not the collection log's tree, which is what
 * D3 requires.
 *
 * <p>Lives in {@code ui} because it must import {@link InterfaceID}, and the zero-RuneLite
 * property of {@code data}, {@code detection} and {@code view} is load-bearing. It is a pure
 * static function anyway, which is why it has a unit test and the rest of this package doesn't.
 */
public final class TopLevelFloater
{
	private TopLevelFloater()
	{
	}

	/**
	 * @param topLevelGroupId what {@code Client.getTopLevelInterfaceId()} returned
	 * @return the FLOATER component id to parent onto, or -1 if this is not a layout we know
	 *     (including -1, which is what the client reports before any layout has loaded)
	 */
	public static int componentId(int topLevelGroupId)
	{
		switch (topLevelGroupId)
		{
			case InterfaceID.TOPLEVEL:
				return InterfaceID.Toplevel.FLOATER;
			case InterfaceID.TOPLEVEL_OSRS_STRETCH:
				return InterfaceID.ToplevelOsrsStretch.FLOATER;
			case InterfaceID.TOPLEVEL_PRE_EOC:
				return InterfaceID.ToplevelPreEoc.FLOATER;
			case InterfaceID.TOPLEVEL_DISPLAY:
				return InterfaceID.ToplevelDisplay.FLOATER;
			case InterfaceID.TOPLEVEL_OSM:
				return InterfaceID.ToplevelOsm.FLOATER;
			case InterfaceID.TOPLEVEL_SPECTATOR:
				return InterfaceID.ToplevelSpectator.FLOATER;
			default:
				return -1;
		}
	}
}

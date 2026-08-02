package com.bossmechanics.ui;

import net.runelite.api.gameval.InterfaceID;

/**
 * Which component hosts our window, for whichever top-level layout the client is currently
 * running (fixed, resizable-stretch, pre-EoC, display, mobile, spectator).
 *
 * <p>{@code UI_HIGHLIGHTS}, not {@code FLOATER} (docs/DECISIONS.md D20). The first attempt used
 * FLOATER on the reasoning that it is a later sibling of MAINMODAL in the cache and so draws on
 * top. In game the window rendered *behind* the collection log, and the widget tree explains
 * why: the collection log is opened onto FLOATER itself, and FLOATER is nested inside GAMEFRAME,
 * which is the *first* root the client draws. Being a dynamic child of FLOATER puts us under the
 * interface node opened onto it.
 *
 * <p>The client's draw order in that layout was {@code c0, GAMEFRAME(c34), c35, c36,
 * MOUSEOVER(c37), UI_HIGHLIGHTS(c98)}. Anything hosted under a later root draws over everything
 * in GAMEFRAME, collection log included. Of the roots after GAMEFRAME, c35 is the 300-wide
 * sidebar and c36 is 1x1, so neither can host a 500x314 window; MOUSEOVER and UI_HIGHLIGHTS are
 * both full parent size. UI_HIGHLIGHTS is drawn last and is only used for occasional highlight
 * overlays, whereas MOUSEOVER backs hover rendering, so UI_HIGHLIGHTS is the one to squat.
 *
 * <p>It also still satisfies D3: it is not the collection log's widget tree, so a Jagex rebuild
 * of group 621 cannot touch us.
 *
 * <p>Lives in {@code ui} because it must import {@link InterfaceID}, and the zero-RuneLite
 * property of {@code data}, {@code detection} and {@code view} is load-bearing. It is a pure
 * static function anyway, which is why it has a unit test and the rest of this package doesn't.
 */
public final class WindowHost
{
	private WindowHost()
	{
	}

	/**
	 * @param topLevelGroupId what {@code Client.getTopLevelInterfaceId()} returned
	 * @return the component id to parent onto, or -1 if this is not a layout we know
	 *     (including -1, which is what the client reports before any layout has loaded)
	 */
	public static int componentId(int topLevelGroupId)
	{
		switch (topLevelGroupId)
		{
			case InterfaceID.TOPLEVEL:
				return InterfaceID.Toplevel.UI_HIGHLIGHTS;
			case InterfaceID.TOPLEVEL_OSRS_STRETCH:
				return InterfaceID.ToplevelOsrsStretch.UI_HIGHLIGHTS;
			case InterfaceID.TOPLEVEL_PRE_EOC:
				return InterfaceID.ToplevelPreEoc.UI_HIGHLIGHTS;
			case InterfaceID.TOPLEVEL_DISPLAY:
				return InterfaceID.ToplevelDisplay.UI_HIGHLIGHTS;
			case InterfaceID.TOPLEVEL_OSM:
				return InterfaceID.ToplevelOsm.UI_HIGHLIGHTS;
			case InterfaceID.TOPLEVEL_SPECTATOR:
				return InterfaceID.ToplevelSpectator.UI_HIGHLIGHTS;
			default:
				return -1;
		}
	}
}

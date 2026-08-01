package com.bossmechanics.ui;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.BossPageIndex;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/**
 * Puts a single native-styled icon button in the collection log header, next to Combat
 * Achievements, on the page of any boss we have data for (docs/DECISIONS.md D15, D19).
 *
 * Deliberately dumb. Nothing under {@code com.bossmechanics.ui} can be unit tested (this
 * project has no Mockito, and every method here needs a live {@link Widget} tree), so every
 * decision this class makes that could be wrong is made somewhere else: which boss a page
 * belongs to is {@link BossPageIndex}'s, and what a click does is the plugin's lambda.
 */
@Slf4j
public class CollectionLogButton
{
	/**
	 * A pair of crossed swords. Chosen as the closest stock "combat/boss" glyph to the
	 * Combat Achievements trophy it sits beside; it is a plain cache sprite, so swapping it
	 * later is a one-line change.
	 */
	private static final int BUTTON_SPRITE = SpriteID.ICON_SWORDS;

	/** Horizontal breathing room between our button and the Combat Achievements one. */
	private static final int GAP = 4;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	private BossPageIndex bossIndex;
	private Consumer<Boss> onOpen;

	/** Our injected child. Never assumed live: {@link #stillAttached} re-checks the tree. */
	private Widget button;

	/** The boss whose page is currently drawn, or null if this page isn't one of ours. */
	private Boss currentBoss;

	// TEMPORARY (issue #4 in-client verification). The diagnostic block below exists only so
	// the first real run reports the numbers needed to confirm button placement. Delete the
	// logDiagnostics call, the method, and this field once positioning is confirmed in game.
	private String lastDiagnosedTitle;

	public void setBossIndex(BossPageIndex bossIndex)
	{
		this.bossIndex = bossIndex;
	}

	/**
	 * What clicking the button does. Kept as a callback so the real interface (issue #5)
	 * replaces one lambda in the plugin instead of changing this class.
	 */
	public void setOnOpen(Consumer<Boss> onOpen)
	{
		this.onOpen = onOpen;
	}

	/** Called from the plugin's startUp. Nothing to build yet: the next page draw builds it. */
	public void onPluginStart()
	{
		button = null;
		currentBoss = null;
	}

	/** Called from the plugin's shutDown so disabling the plugin doesn't strand a button on screen. */
	public void onPluginStop()
	{
		clientThread.invokeLater(() ->
		{
			if (button != null)
			{
				button.setHidden(true);
			}
			button = null;
			currentBoss = null;
		});
	}

	/**
	 * The collection log redraws its list through this script on open, tab switch, boss
	 * selection and search, which is why it is the hook rather than WidgetLoaded on group 621
	 * (that fires once, on open). Core RuneLite's ChatCommandsPlugin reads the page title the
	 * same way.
	 */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST)
		{
			onPageDrawn();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.COLLECTION)
		{
			// Hide before dropping the reference. The anchor is a top-level component of
			// group 621, so its parent may be the container the log was opened onto rather
			// than part of 621 itself. If so, closing the log does not tear our button down
			// and dropping the reference first would strand it on screen with nothing left
			// able to hide it. Hiding an already-detached widget is harmless.
			if (button != null)
			{
				button.setHidden(true);
			}
			button = null;
			currentBoss = null;
			lastDiagnosedTitle = null;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			// The interface tree is torn down on these transitions, so the reference is
			// already invalid. Drop it without touching it.
			button = null;
			currentBoss = null;
			lastDiagnosedTitle = null;
		}
	}

	/**
	 * Remove-then-maybe-add, run on every page draw, so the button can never be left over on
	 * a page that shouldn't have it.
	 *
	 * Parenting to the Combat Achievements button's own parent sidesteps D14's problem of
	 * picking a layer that silently never draws: a sibling that visibly draws proves the layer
	 * draws. No anchor means no button, on purpose.
	 */
	private void onPageDrawn()
	{
		Widget anchor = client.getWidget(InterfaceID.Collection.COMBAT_ACHIEVEMENTS);
		Widget parent = anchor == null ? null : anchor.getParent();
		if (parent == null)
		{
			button = null;
			currentBoss = null;
			return;
		}

		if (!stillAttached(parent))
		{
			button = parent.createChild(-1, WidgetType.GRAPHIC);
			button.setSpriteId(BUTTON_SPRITE);
			button.setOriginalWidth(anchor.getWidth());
			button.setOriginalHeight(anchor.getHeight());
			button.setAction(0, "Boss Mechanics");
			button.setNoClickThrough(true);
			button.setHasListener(true);
			button.setOnOpListener((JavaScriptCallback) e -> open());
		}

		String title = pageTitle();
		currentBoss = bossIndex == null ? null : bossIndex.forPageTitle(title);

		button.setHidden(currentBoss == null);

		// Read out of the cache (group 621 child 21): the Combat Achievements button is
		// x=0 y=0 w=50 h=25 with xPositionMode 2, i.e. right-aligned. Its x is measured
		// from the RIGHT edge, so a larger x sits further LEFT on screen. We match its
		// position mode and add its width, which places us just left of it. Subtracting,
		// as absolute-left coordinates would require, sends the button off the far side.
		button.setXPositionMode(anchor.getXPositionMode());
		button.setYPositionMode(anchor.getYPositionMode());
		button.setOriginalX(anchor.getOriginalX() + anchor.getWidth() + GAP);
		button.setOriginalY(anchor.getOriginalY());
		button.revalidate();

		// D14: a child computes nothing on its own; the parent layer runs the layout pass.
		parent.revalidate();

		logDiagnostics(title, anchor, parent);
	}

	private String pageTitle()
	{
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		if (header == null || header.getChild(0) == null)
		{
			return null;
		}
		return header.getChild(0).getText();
	}

	/**
	 * Whether the child we created is still in the parent's list, by identity.
	 *
	 * A {@code button != null} check would not do: Jagex's scripts rebuild this header, and a
	 * rebuild drops our child without telling us. Scanning is self-healing in both directions
	 * (recreate when we were wiped, reuse when we weren't), which is what keeps this to one
	 * dynamic child per collection log open rather than one per page draw.
	 */
	private boolean stillAttached(Widget parent)
	{
		if (button == null)
		{
			return false;
		}

		Widget[] children = parent.getDynamicChildren();
		if (children == null)
		{
			return false;
		}

		for (Widget child : children)
		{
			if (child == button)
			{
				return true;
			}
		}
		return false;
	}

	/** Runs on the client thread (op listeners always do), so reading plugin state here is safe. */
	private void open()
	{
		if (currentBoss != null && onOpen != null)
		{
			onOpen.accept(currentBoss);
		}
	}

	/**
	 * TEMPORARY (issue #4 in-client verification). Delete this method and its call site once
	 * button placement is confirmed in game. Deduped on page title so it reports once per page
	 * visited, not once per redraw.
	 */
	private void logDiagnostics(String title, Widget anchor, Widget parent)
	{
		if (Objects.equals(title, lastDiagnosedTitle))
		{
			return;
		}
		lastDiagnosedTitle = title;

		Widget[] children = parent.getDynamicChildren();
		log.info("Collection log page '{}' -> boss {}", title, currentBoss == null ? "none" : currentBoss.getId());
		log.info("  CA anchor: parentId={} hidden={} size={}x{} original={}x{} originalPos=({},{}) relativePos=({},{}) xPosMode={} yPosMode={}",
			anchor.getParentId(), anchor.isHidden(),
			anchor.getWidth(), anchor.getHeight(),
			anchor.getOriginalWidth(), anchor.getOriginalHeight(),
			anchor.getOriginalX(), anchor.getOriginalY(),
			anchor.getRelativeX(), anchor.getRelativeY(),
			anchor.getXPositionMode(), anchor.getYPositionMode());
		log.info("  our button: hidden={} size={}x{} originalPos=({},{}) relativePos=({},{}); parent dynamic children after injection={}",
			button.isHidden(), button.getWidth(), button.getHeight(),
			button.getOriginalX(), button.getOriginalY(),
			button.getRelativeX(), button.getRelativeY(),
			children == null ? 0 : children.length);

		// The anchor is a bare 50x25 layer in the cache: spriteId -1, no stored children.
		// Its icon is built at runtime by a CS2 script, so the only way to learn the icon's
		// sprite and true size (which ours should match) is to read it from a live client.
		logIconChildren("CA static", anchor.getChildren());
		logIconChildren("CA dynamic", anchor.getDynamicChildren());
		log.info("  CA anchor spriteId={} type={}", anchor.getSpriteId(), anchor.getType());
	}

	private void logIconChildren(String label, Widget[] children)
	{
		if (children == null || children.length == 0)
		{
			log.info("  {}: none", label);
			return;
		}

		for (Widget child : children)
		{
			if (child == null)
			{
				continue;
			}
			log.info("  {}: type={} sprite={} size={}x{} original={}x{} pos=({},{})",
				label, child.getType(), child.getSpriteId(),
				child.getWidth(), child.getHeight(),
				child.getOriginalWidth(), child.getOriginalHeight(),
				child.getRelativeX(), child.getRelativeY());
		}
	}
}

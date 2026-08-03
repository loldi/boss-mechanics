package com.bossmechanics.ui;

import com.bossmechanics.view.Ellipsize;
import com.bossmechanics.view.MechanicRow;
import com.bossmechanics.view.MechanicsView;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;

/**
 * The window's left-hand column: a "Mechanic" header band with the reveal toggle beside it, over
 * one 22-tall row per mechanic in schema order, plus the scrollbar.
 *
 * <p>Reads {@link MechanicRow} and nothing else. It never asks whether a mechanic is discovered
 * or revealed, only whether the row it was handed is {@code locked}, which is what keeps the D10
 * invariant out of reach of the interface.
 *
 * <p>Rows are one line now, not three. The Combat Achievements screen puts the detail in the right
 * column, so a row carries a name and a phase tag and nothing else — which is also what makes
 * every row the same height, and the scroll maths trivially correct.
 */
final class MechanicsList
{
	/** 717 child 6: the left column is 190 wide. */
	static final int COLUMN_WIDTH = 190;

	private static final int SCROLLBAR_INSET = 1;
	private static final int LIST_WIDTH = COLUMN_WIDTH - MechanicsScrollbar.WIDTH - (2 * SCROLLBAR_INSET);

	private static final int ROW_HEIGHT = 22;
	private static final int NAME_X = 4;
	private static final int PHASE_INSET = 4;

	/**
	 * The zone reserved for the phase tag at the row's right end, ellipsis budget included:
	 * "Phase 1" in PLAIN_12 is ~40px, plus the inset and a 2px gap. The name is fitted into what
	 * is left of the row rather than given the full width, because two full-width texts on one
	 * line is exactly how "Respiratory Systems" ran under its tag in game.
	 */
	private static final int PHASE_WIDTH = 50;
	private static final int HEADER_TITLE_X = 2;
	private static final int REVEAL_WIDTH = 66;

	/** The collection log's own selected-row wash. Sits under the row's text, never over it. */
	private static final int SELECTED_COLOR = 0x2E2B23;

	private final Widget header;
	private final Widget column;
	private final MechanicsView view;
	private final Consumer<String> onSelected;
	private final Runnable onRevealToggled;

	/** One per row, all hidden but the selected one. Rebuilt with the rows, so never stale. */
	private final Map<String, Widget> selectionWashes = new HashMap<>();

	/**
	 * @param header the 190-wide header band layer, already positioned and sized
	 * @param column the 190-wide column layer below it, already positioned
	 * @param onSelected fired with the clicked row's mechanic id
	 * @param onRevealToggled fired when "View All" / "Hide All" is clicked
	 */
	MechanicsList(Widget header, Widget column, MechanicsView view, Consumer<String> onSelected,
		Runnable onRevealToggled)
	{
		this.header = header;
		this.column = column;
		this.view = view;
		this.onSelected = onSelected;
		this.onRevealToggled = onRevealToggled;
	}

	void build()
	{
		header();
		rows();
	}

	/** Washes the named row and only that row. Null clears the selection entirely. */
	void highlight(String mechanicId)
	{
		for (Map.Entry<String, Widget> wash : selectionWashes.entrySet())
		{
			wash.getValue().setHidden(!wash.getKey().equals(mechanicId));
			wash.getValue().revalidate();
		}
		// D22 correction of D14: each wash already revalidated itself above; this only relays out
		// the column against its own parent, which never changes size from a selection change.
		column.revalidate();
	}

	private void header()
	{
		int height = header.getOriginalHeight();

		Widget title = Widgets.text(header, "Mechanic", FontID.BOLD_12, Widgets.ORANGE);
		title.setOriginalX(HEADER_TITLE_X);
		title.setOriginalWidth(COLUMN_WIDTH - HEADER_TITLE_X);
		title.setOriginalHeight(height);
		title.setYTextAlignment(WidgetTextAlignment.CENTER);
		title.revalidate();

		revealButton(height);
		header.revalidate();
	}

	/**
	 * "View All" / "Hide All", moved out of the title bar and into the list's own header, where
	 * the Combat Achievements screen puts its column controls. The label comes from the view model
	 * and the listener only reports the flip; persisting it and rebuilding is the plugin's, which
	 * is what keeps the reveal state out of the interface entirely.
	 */
	private void revealButton(int height)
	{
		Widget toggle = Widgets.text(header, view.revealActionLabel(), FontID.PLAIN_12, Widgets.ORANGE);
		toggle.setOriginalX(0);
		toggle.setOriginalWidth(REVEAL_WIDTH);
		toggle.setOriginalHeight(height);
		toggle.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		toggle.setXTextAlignment(WidgetTextAlignment.RIGHT);
		toggle.setYTextAlignment(WidgetTextAlignment.CENTER);
		toggle.setAction(0, view.revealActionLabel());
		toggle.setNoClickThrough(true);
		toggle.setHasListener(true);
		toggle.setOnOpListener((JavaScriptCallback) event -> onRevealToggled.run());
		toggle.setOnMouseOverListener((JavaScriptCallback) event -> toggle.setTextColor(Widgets.ORANGE_HOVER));
		toggle.setOnMouseLeaveListener((JavaScriptCallback) event -> toggle.setTextColor(Widgets.ORANGE));
		toggle.revalidate();
	}

	private void rows()
	{
		int height = column.getOriginalHeight();
		Widget list = Widgets.layer(column, 0, 0, LIST_WIDTH, height);

		Widget bar = Widgets.layer(column, SCROLLBAR_INSET, SCROLLBAR_INSET,
			MechanicsScrollbar.WIDTH, height - (2 * SCROLLBAR_INSET));
		bar.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		bar.setHeightMode(WidgetSizeMode.MINUS);
		bar.setOriginalHeight(2 * SCROLLBAR_INSET);
		bar.revalidate();

		int y = 0;
		for (MechanicRow row : view.getRows())
		{
			row(list, row, y);
			y += ROW_HEIGHT;
		}

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(
			list, bar, height - (2 * SCROLLBAR_INSET), height, y);
		scrollbar.build();

		// A wheel event over a row is not guaranteed to reach the list behind it, so every row
		// scrolls the list too.
		Widget[] children = list.getDynamicChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				scrollbar.listenForWheel(child);
			}
		}

		// The Combat Achievements section frame (docs/DECISIONS.md D26), a sibling drawn after the
		// rows and scrollbar so it overdraws their edges rather than sitting underneath them.
		Widgets.sectionBorder(column, 0, 0, COLUMN_WIDTH, height);

		column.revalidate();
	}

	private void row(Widget list, MechanicRow row, int y)
	{
		Widget layer = Widgets.layer(list, 0, y, LIST_WIDTH, ROW_HEIGHT);
		layer.setAction(0, "Select");
		layer.setNoClickThrough(true);
		layer.setHasListener(true);
		layer.setOnOpListener((JavaScriptCallback) event -> onSelected.accept(row.getMechanicId()));

		// First child, so the wash sits behind the row's own text rather than over it.
		Widget wash = Widgets.filled(layer, 0, 0, LIST_WIDTH, ROW_HEIGHT, SELECTED_COLOR);
		wash.setHidden(true);
		selectionWashes.put(row.getMechanicId(), wash);

		int nameWidth = LIST_WIDTH - NAME_X - (row.getPhase() != null ? PHASE_WIDTH : 0);
		int resting = row.isLocked() ? Widgets.GREY : Widgets.ORANGE;
		Widget name = Widgets.text(layer, row.getName(),
			row.isLocked() ? FontID.PLAIN_12 : FontID.BOLD_12, resting);
		name.setOriginalX(NAME_X);
		name.setOriginalWidth(nameWidth);
		name.setOriginalHeight(ROW_HEIGHT);
		name.setYTextAlignment(WidgetTextAlignment.CENTER);
		fitName(name, row.getName(), nameWidth);
		name.revalidate();

		layer.setOnMouseOverListener((JavaScriptCallback) event -> name.setTextColor(Widgets.ORANGE_HOVER));
		layer.setOnMouseLeaveListener((JavaScriptCallback) event -> name.setTextColor(resting));

		// Null for a locked row (that is the whole of the reveal payoff) and for a mechanic that
		// simply has no phase.
		if (row.getPhase() != null)
		{
			phase(layer, row.getPhase());
		}
	}

	/**
	 * Replaces the name with its longest fitting prefix + "..." when the row's font says the full
	 * name overruns {@code maxWidth}. The fitting rule lives in {@link Ellipsize} (tested); this
	 * only supplies the real font metrics, which is why the null-font fallback just keeps the
	 * full name — the pre-fix behaviour, not a blank row.
	 */
	private static void fitName(Widget name, String fullName, int maxWidth)
	{
		FontTypeFace font = name.getFont();
		if (font != null)
		{
			name.setText(Ellipsize.fit(fullName, font::getTextWidth, maxWidth));
		}
	}

	private void phase(Widget layer, String text)
	{
		Widget widget = Widgets.text(layer, text, FontID.PLAIN_12, Widgets.GREY);
		widget.setOriginalX(PHASE_INSET);
		widget.setOriginalWidth(PHASE_WIDTH - PHASE_INSET);
		widget.setOriginalHeight(ROW_HEIGHT);
		widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		widget.setXTextAlignment(WidgetTextAlignment.RIGHT);
		widget.setYTextAlignment(WidgetTextAlignment.CENTER);
		widget.revalidate();
	}
}

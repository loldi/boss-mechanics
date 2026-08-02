package com.bossmechanics.ui;

import com.bossmechanics.view.MechanicRow;
import com.bossmechanics.view.MechanicsView;
import java.util.function.Consumer;
import net.runelite.api.FontID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;

/**
 * The window's left-hand column: one row per mechanic, in schema order, plus the scrollbar.
 *
 * <p>Reads {@link MechanicRow} and nothing else. It never asks whether a mechanic is discovered
 * or revealed, only whether the row it was handed is {@code locked}, which is what keeps the D10
 * invariant out of reach of the interface.
 */
final class MechanicsList
{
	/** 621 LIST child 9 is 200 wide; 621 BOSS_SCROLLBAR child 13 is 16 wide, inset (1,1). */
	static final int COLUMN_WIDTH = 200;
	private static final int SCROLLBAR_INSET = 1;
	private static final int LIST_WIDTH = COLUMN_WIDTH - MechanicsScrollbar.WIDTH - (2 * SCROLLBAR_INSET);

	/**
	 * Row heights are fixed per state. OSRS text widgets wrap but expose no wrapped height, so a
	 * description block cannot be measured; these are two-line allowances, tuned by eye. The
	 * scroll height is the SUM OF ACTUAL ROW HEIGHTS, never rows x a constant.
	 */
	private static final int ROW_HEIGHT_LOCKED = 22;
	private static final int NAME_HEIGHT = 15;
	private static final int DESCRIPTION_HEIGHT = 24;
	private static final int COUNTERPLAY_HEIGHT = 22;
	private static final int PADDING = 4;
	private static final int ROW_HEIGHT_DISCOVERED =
		PADDING + NAME_HEIGHT + DESCRIPTION_HEIGHT + COUNTERPLAY_HEIGHT + PADDING;

	private static final int TEXT_WIDTH = LIST_WIDTH - (2 * PADDING);
	private static final int LINE_HEIGHT = 12;

	private MechanicsList()
	{
	}

	/**
	 * @param column the 200-wide column layer, already positioned
	 * @param height the column's height, known because the window is a fixed 500x314
	 * @param onSelected fired with the clicked row's mechanic id; #6's hook
	 */
	static void build(Widget column, int height, MechanicsView view, Consumer<String> onSelected)
	{
		Widget list = Widgets.layer(column, 0, 0, LIST_WIDTH, height);

		Widget bar = Widgets.layer(column, SCROLLBAR_INSET, SCROLLBAR_INSET,
			MechanicsScrollbar.WIDTH, height - (2 * SCROLLBAR_INSET));
		bar.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		bar.setHeightMode(WidgetSizeMode.MINUS);
		bar.setOriginalHeight(2 * SCROLLBAR_INSET);
		bar.revalidate();

		int contentHeight = rows(list, view, onSelected);

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(
			list, bar, height - (2 * SCROLLBAR_INSET), height, contentHeight);
		scrollbar.build();

		// A wheel event over a row is not guaranteed to reach the list behind it, so every row
		// scrolls the list too.
		Widget[] children = list.getDynamicChildren();
		if (children != null)
		{
			for (Widget row : children)
			{
				scrollbar.listenForWheel(row);
			}
		}

		column.revalidate();
	}

	/** @return the summed height of every row, which is what the list's scroll height must be. */
	private static int rows(Widget list, MechanicsView view, Consumer<String> onSelected)
	{
		int y = 0;
		for (MechanicRow row : view.getRows())
		{
			int height = row.isLocked() ? ROW_HEIGHT_LOCKED : ROW_HEIGHT_DISCOVERED;
			row(list, row, y, height, onSelected);
			y += height;
		}
		return y;
	}

	private static void row(Widget list, MechanicRow row, int y, int height, Consumer<String> onSelected)
	{
		Widget layer = Widgets.layer(list, 0, y, LIST_WIDTH, height);
		layer.setAction(0, "Select");
		layer.setNoClickThrough(true);
		layer.setHasListener(true);
		layer.setOnOpListener((JavaScriptCallback) event -> onSelected.accept(row.getMechanicId()));

		int resting = row.isLocked() ? Widgets.GREY : Widgets.ORANGE;
		Widget name = Widgets.text(layer, heading(row),
			row.isLocked() ? FontID.PLAIN_12 : FontID.BOLD_12, resting);
		name.setOriginalX(PADDING);
		name.setOriginalY(PADDING);
		name.setOriginalWidth(TEXT_WIDTH);
		name.setOriginalHeight(NAME_HEIGHT);
		name.revalidate();

		layer.setOnMouseOverListener((JavaScriptCallback) event -> name.setTextColor(Widgets.ORANGE_HOVER));
		layer.setOnMouseLeaveListener((JavaScriptCallback) event -> name.setTextColor(resting));

		if (row.isLocked())
		{
			return;
		}

		wrapped(layer, row.getDescription(), PADDING + NAME_HEIGHT, DESCRIPTION_HEIGHT, Widgets.WHITE);
		wrapped(layer, row.getCounterplay(), PADDING + NAME_HEIGHT + DESCRIPTION_HEIGHT,
			COUNTERPLAY_HEIGHT, Widgets.GREY);
	}

	/**
	 * A discovered row can carry its phase; a locked one is a bare "???" with no phase tag
	 * (resolved fork), so revealing actually delivers something.
	 */
	private static String heading(MechanicRow row)
	{
		return row.getPhase() == null ? row.getName() : row.getName() + " (" + row.getPhase() + ")";
	}

	private static void wrapped(Widget parent, String content, int y, int height, int color)
	{
		Widget widget = Widgets.text(parent, content, FontID.PLAIN_12, color);
		widget.setOriginalX(PADDING);
		widget.setOriginalY(y);
		widget.setOriginalWidth(TEXT_WIDTH);
		widget.setOriginalHeight(height);
		widget.setLineHeight(LINE_HEIGHT);
		widget.revalidate();
	}
}

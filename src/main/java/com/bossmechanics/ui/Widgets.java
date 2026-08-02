package com.bossmechanics.ui;

import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;

/**
 * The handful of widget shapes the Boss Mechanics window is made of, and the colours it draws
 * them in. Most of these end in {@code revalidate()}; {@link #text} does not, so revalidate it yourself, because a freshly created child that
 * is never revalidated is built, unhidden and invisible (docs/DECISIONS.md D14).
 *
 * <p>Deliberately not shared with {@link CollectionLogButton}: sharing would mean restructuring
 * that class, which is a follow-up once the animated preview (#6) lands.
 */
final class Widgets
{
	static final int WHITE = 0xFFFFFF;
	/** The collection log's own list colours; script 4782 uses 0xFF9C1F for hover. */
	static final int ORANGE = 0xFF981F;
	static final int ORANGE_HOVER = 0xFF9C1F;
	static final int GREY = 0x9F9F9F;

	/** RuneLite opacity is inverted: 0 is fully opaque, 255 is invisible. */
	private static final int OPAQUE = 0;

	/**
	 * The Combat Achievements button's own nine-slice frame. Duplicated from
	 * {@link CollectionLogButton} on purpose: sharing it means restructuring that class, which is
	 * a follow-up once the animated preview (#6) lands.
	 */
	private static final int SPRITE_BACKGROUND = 297;
	private static final int SPRITE_CORNER_TL = 913;
	private static final int SPRITE_CORNER_TR = 914;
	private static final int SPRITE_CORNER_BL = 915;
	private static final int SPRITE_CORNER_BR = 916;
	private static final int SPRITE_EDGE_LEFT = 917;
	private static final int SPRITE_EDGE_TOP = 918;
	private static final int SPRITE_EDGE_RIGHT = 919;
	private static final int SPRITE_EDGE_BOTTOM = 920;

	/**
	 * Frame thickness. The corner sprites are 9x9 so the corners are fixed; the edge sprites are
	 * 3px thick naturally and get stretched to this, exactly as the Combat Achievements button
	 * does at 50x25. If the border reads too heavy at window scale, this is the one number to tune.
	 */
	static final int FRAME = 9;

	private Widgets()
	{
	}

	/**
	 * The Combat Achievements button's construction at window scale: background fill first, then
	 * the nine-slice border over it. Later children draw above earlier ones, so order matters.
	 */
	static void frame(Widget parent, int width, int height)
	{
		int right = width - FRAME;
		int bottom = height - FRAME;
		int innerWidth = width - (2 * FRAME);
		int innerHeight = height - (2 * FRAME);

		sprite(parent, SPRITE_BACKGROUND, 1, 1, width - 2, height - 2, false);

		sprite(parent, SPRITE_CORNER_TL, 0, 0, FRAME, FRAME, false);
		sprite(parent, SPRITE_CORNER_TR, right, 0, FRAME, FRAME, false);
		sprite(parent, SPRITE_CORNER_BL, 0, bottom, FRAME, FRAME, false);
		sprite(parent, SPRITE_CORNER_BR, right, bottom, FRAME, FRAME, false);
		sprite(parent, SPRITE_EDGE_LEFT, 0, FRAME, FRAME, innerHeight, false);
		sprite(parent, SPRITE_EDGE_RIGHT, right, FRAME, FRAME, innerHeight, false);
		sprite(parent, SPRITE_EDGE_TOP, FRAME, 0, innerWidth, FRAME, false);
		sprite(parent, SPRITE_EDGE_BOTTOM, FRAME, bottom, innerWidth, FRAME, false);
	}

	static Widget layer(Widget parent, int x, int y, int width, int height)
	{
		Widget layer = parent.createChild(-1, WidgetType.LAYER);
		layer.setOriginalX(x);
		layer.setOriginalY(y);
		layer.setOriginalWidth(width);
		layer.setOriginalHeight(height);
		layer.revalidate();
		return layer;
	}

	static Widget text(Widget parent, String content, int fontId, int color)
	{
		Widget widget = parent.createChild(-1, WidgetType.TEXT);
		widget.setText(content);
		widget.setFontId(fontId);
		widget.setTextColor(color);
		widget.setTextShadowed(true);
		widget.setXTextAlignment(WidgetTextAlignment.LEFT);
		widget.setYTextAlignment(WidgetTextAlignment.TOP);
		return widget;
	}

	/**
	 * @param tiled false stretches the sprite to the widget's size, which is how the nine-slice
	 *     frame's uniform edges scale; true repeats it, which is what script 4782's 1x27 progress
	 *     track and fill need.
	 */
	static Widget sprite(Widget parent, int spriteId, int x, int y, int width, int height, boolean tiled)
	{
		Widget part = parent.createChild(-1, WidgetType.GRAPHIC);
		part.setSpriteId(spriteId);
		part.setOriginalX(x);
		part.setOriginalY(y);
		part.setOriginalWidth(Math.max(0, width));
		part.setOriginalHeight(Math.max(0, height));
		part.setSpriteTiling(tiled);
		part.revalidate();
		return part;
	}

	/** A solid block. Colour goes through setTextColor, as SireWidgetSpike's backdrop proved. */
	static Widget filled(Widget parent, int x, int y, int width, int height, int color)
	{
		Widget block = rectangle(parent, x, y, width, height, color);
		block.setFilled(true);
		block.setOpacity(OPAQUE);
		block.revalidate();
		return block;
	}

	/** An unfilled RECTANGLE is a 1px border. */
	static Widget outline(Widget parent, int x, int y, int width, int height, int color)
	{
		Widget border = rectangle(parent, x, y, width, height, color);
		border.setFilled(false);
		border.revalidate();
		return border;
	}

	private static Widget rectangle(Widget parent, int x, int y, int width, int height, int color)
	{
		Widget rectangle = parent.createChild(-1, WidgetType.RECTANGLE);
		rectangle.setTextColor(color);
		rectangle.setOriginalX(x);
		rectangle.setOriginalY(y);
		rectangle.setOriginalWidth(width);
		rectangle.setOriginalHeight(height);
		return rectangle;
	}
}

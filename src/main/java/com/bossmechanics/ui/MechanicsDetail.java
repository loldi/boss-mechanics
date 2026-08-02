package com.bossmechanics.ui;

import com.bossmechanics.view.MechanicRow;
import com.bossmechanics.view.PreviewSpec;
import java.util.function.IntUnaryOperator;
import net.runelite.api.FontID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetType;

/**
 * The window's right-hand column: a WIKI button in the header band, the animated model box (#6),
 * then the selected mechanic's name, description and counterplay, with a dim laid over the lot
 * while the selection is locked.
 *
 * <p><b>Everything here is built once and then mutated.</b> {@link #show} rewrites text and the
 * one model widget, and never deletes a child: a {@code deleteAllChildren()} here would kill the
 * model mid-animation every time the player clicked a different row. Only the window's own root
 * is ever emptied (D19).
 */
final class MechanicsDetail
{
	/** 717 child 13 scaled to our 494-wide content: the right column is 291 wide. */
	static final int COLUMN_WIDTH = 291;

	private static final int MODEL_HEIGHT = 110;
	private static final int MODEL_FILL = 0x0E0E0C;
	private static final int MODEL_BORDER = 0x474645;

	/** {@link IntUnaryOperator#applyAsInt} result meaning "no model resolved for this npc". */
	private static final int UNKNOWN_MODEL = -1;

	private static final int NAME_Y = 116;
	private static final int NAME_HEIGHT = 15;
	private static final int DESCRIPTION_Y = 133;
	private static final int DESCRIPTION_HEIGHT = 36;
	private static final int COUNTERPLAY_Y = 171;
	private static final int COUNTERPLAY_HEIGHT = 41;
	private static final int LINE_HEIGHT = 12;

	/**
	 * The WIKI button, cache sprites 2420 (resting) and 2421 (hover), both 40x14 and both
	 * literally reading "WIKI". Positioned like the collection log's own header buttons: inset
	 * from the right, vertically centred in the 23-tall band.
	 */
	private static final int SPRITE_WIKI = 2420;
	private static final int SPRITE_WIKI_HOVER = 2421;
	private static final int WIKI_WIDTH = 40;
	private static final int WIKI_HEIGHT = 14;
	private static final int WIKI_Y = 4;

	/**
	 * Script 4808's own idiom for a locked Combat Achievements entry: a black fill at
	 * {@code cc_settrans 150} over the whole panel. RuneLite opacity is INVERTED, so 150 is
	 * translucent, not nearly-solid.
	 */
	private static final int DIM_OPACITY = 150;

	private final Widget header;
	private final Widget column;
	private final IntUnaryOperator modelForNpc;
	private final Runnable onWikiOpened;

	private Widget model;
	private Widget name;
	private Widget description;
	private Widget counterplay;
	private Widget dim;

	/**
	 * @param header the 291-wide header band layer, already positioned and sized
	 * @param column the 291-wide column layer below it, already positioned
	 * @param modelForNpc resolves an npc id to the cache model id to render ({@link #UNKNOWN_MODEL}
	 *     if none); supplied as a lambda so this package never imports
	 *     {@code client.getNpcDefinition()}
	 * @param onWikiOpened fired when the WIKI button is clicked; opening the URL is the plugin's
	 *     job, so this package never imports {@code LinkBrowser}
	 */
	MechanicsDetail(Widget header, Widget column, IntUnaryOperator modelForNpc, Runnable onWikiOpened)
	{
		this.header = header;
		this.column = column;
		this.modelForNpc = modelForNpc;
		this.onWikiOpened = onWikiOpened;
	}

	void build()
	{
		wikiButton();

		// A LAYER, not the RECTANGLE itself: the model needs a LAYER parent to render, since
		// nested dynamic children are only known to render under one (D19). The border is a
		// sibling drawn afterwards, so the model can never overdraw its own frame.
		int height = column.getOriginalHeight();
		Widget modelBox = Widgets.layer(column, 0, 0, COLUMN_WIDTH, MODEL_HEIGHT);
		Widgets.filled(modelBox, 0, 0, COLUMN_WIDTH, MODEL_HEIGHT, MODEL_FILL);

		// Created once, here, and mutated by every show() rather than recreated (issue #6): a
		// fresh MODEL widget per selection would glitch mid-play and pile up like the D22 leak.
		model = modelBox.createChild(-1, WidgetType.MODEL);
		model.setModelType(WidgetModelType.MODEL);
		// Rotation is fixed at what the spike validated (docs/DECISIONS.md D14); both axes must
		// stay within 0-2047 or the client crashes, which a constant zero trivially satisfies.
		model.setRotationX(0);
		model.setRotationY(0);
		model.setRotationZ(0);
		model.setOriginalX(0);
		model.setOriginalY(0);
		model.setOriginalWidth(COLUMN_WIDTH);
		model.setOriginalHeight(MODEL_HEIGHT);
		model.revalidate();

		Widgets.outline(column, 0, 0, COLUMN_WIDTH, MODEL_HEIGHT, MODEL_BORDER);

		name = text("", FontID.BOLD_12, Widgets.ORANGE, NAME_Y, NAME_HEIGHT);
		description = text("", FontID.PLAIN_12, Widgets.WHITE, DESCRIPTION_Y, DESCRIPTION_HEIGHT);
		counterplay = text("", FontID.PLAIN_12, Widgets.ORANGE, COUNTERPLAY_Y, COUNTERPLAY_HEIGHT);

		// Last, so it covers the box, the model and the text.
		dim = Widgets.filled(column, 0, 0, COLUMN_WIDTH, height, 0x000000);
		dim.setOpacity(DIM_OPACITY);
		dim.setHidden(true);
		dim.revalidate();

		column.revalidate();
	}

	/**
	 * Swaps in a different mechanic's text and preview. Mutates in place and revalidates each
	 * widget and the column, which is what keeps the one model widget playing without a rebuild.
	 *
	 * @param row null when there is nothing to show (a boss with no mechanics), which blanks the
	 *     panel and hides the model rather than leaving the previous mechanic's stranded
	 */
	void show(MechanicRow row)
	{
		// A locked row still fills the panel with "???" and empty strings, so the layout never
		// reflows; the dim is what says "you have not found this yet".
		set(name, row == null ? "" : row.getName());
		set(description, row == null ? "" : row.getDescription());
		set(counterplay, row == null ? "" : row.getCounterplay());

		showModel(row == null ? PreviewSpec.hidden() : row.getPreview());

		dim.setHidden(row == null || !row.isLocked());
		dim.revalidate();

		column.revalidate();
	}

	/**
	 * Mutates the one persistent model widget rather than recreating it (issue #6): swaps model
	 * id, animation and zoom, then hides it outright for a locked row or an npc this client has
	 * no model for, so a locked mechanic can never leak through the preview.
	 */
	private void showModel(PreviewSpec preview)
	{
		int modelId = modelForNpc.applyAsInt(preview.getNpcId());
		boolean visible = preview.isVisible() && modelId != UNKNOWN_MODEL;

		model.setModelId(modelId);
		model.setAnimationId(preview.getAnimationId());
		model.setModelZoom(preview.getZoom());
		model.setHidden(!visible);
		model.revalidate();
	}

	private void set(Widget widget, String content)
	{
		widget.setText(content);
		widget.revalidate();
	}

	private Widget text(String content, int fontId, int color, int y, int height)
	{
		Widget widget = Widgets.text(column, content, fontId, color);
		widget.setOriginalY(y);
		widget.setOriginalWidth(COLUMN_WIDTH);
		widget.setOriginalHeight(height);
		widget.setLineHeight(LINE_HEIGHT);
		widget.revalidate();
		return widget;
	}

	private void wikiButton()
	{
		Widget button = Widgets.sprite(header, SPRITE_WIKI, 0, WIKI_Y, WIKI_WIDTH, WIKI_HEIGHT, false);
		button.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		button.setAction(0, "Open");
		button.setNoClickThrough(true);
		button.setHasListener(true);
		button.setOnOpListener((JavaScriptCallback) event -> onWikiOpened.run());
		button.setOnMouseOverListener((JavaScriptCallback) event -> button.setSpriteId(SPRITE_WIKI_HOVER));
		button.setOnMouseLeaveListener((JavaScriptCallback) event -> button.setSpriteId(SPRITE_WIKI));
		button.revalidate();

		header.revalidate();
	}
}

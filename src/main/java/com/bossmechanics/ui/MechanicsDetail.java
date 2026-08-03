package com.bossmechanics.ui;

import com.bossmechanics.view.MechanicRow;
import com.bossmechanics.view.PreviewSpec;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.FontID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.api.widgets.WidgetType;

/**
 * The window's right-hand column: the animated model box (#6), then the selected mechanic's name,
 * description and counterplay, with a dim laid over the lot while the selection is locked. The
 * WIKI button used to live in this column's own header band; it now lives in the window's title
 * bar instead (docs/DECISIONS.md D25, Fork 1 resolved: Option B), which is why this class no
 * longer takes a header widget or an {@code onWikiOpened} callback.
 *
 * <p><b>Text is built once and then mutated.</b> {@link #show} rewrites the name, description and
 * counterplay text in place and never deletes a child: a {@code deleteAllChildren()} here would
 * blow away whichever animation is currently playing every time the player clicked a different
 * row. Only the window's own root is ever emptied (D19).
 *
 * <p><b>The model widget is a pool keyed by animation id, not a single mutated widget
 * (docs/DECISIONS.md D24).</b> The client keeps a MODEL widget's animation frame counter on the
 * widget itself, and the only thing that ever zeroes it is widget creation ({@code createChild});
 * {@code Widget.setAnimationId} never resets it, and there is no RuneLite API that does. Swapping
 * a live widget from a long sequence to a shorter one therefore leaves the frame counter past the
 * new sequence's frame count, and the client's own draw loop crashes hard
 * ({@code ArrayIndexOutOfBoundsException}) on the very next frame — this is exactly what issue #6
 * (PR #43) hit in game. So each pool widget's animation id is set exactly once, at creation, and
 * {@link #showModel} only ever mutates {@code setModelId}/{@code setModelZoom}/{@code setHidden}
 * on it afterward. The pool is bounded by the distinct animation count for one boss (single
 * digits in practice) and lives on this instance; {@link BossMechanicsWindow} discards and
 * recreates the whole {@code MechanicsDetail} (widgets included) on every rebuild, so a stale pool
 * entry can never outlive the widget it points at.
 */
final class MechanicsDetail
{
	/** 717 child 13 scaled to our 494-wide content: the right column is 291 wide. */
	static final int COLUMN_WIDTH = 291;

	/**
	 * The whole right column's height, header band folded in (docs/DECISIONS.md D25, Fork 1
	 * resolved: Option B) — the column now starts where that band used to and runs to the same
	 * bottom margin the list column does: {@code CONTENT_HEIGHT - COLUMN_HEADER_Y - COLUMN_INSET}
	 * in {@link BossMechanicsWindow}, which builds the band this size. Package-visible so
	 * {@code MechanicsDetailPreviewTest} can assert the text block still fits inside it without a
	 * live widget tree.
	 */
	static final int COLUMN_HEIGHT = 235;

	/**
	 * Fit-zoom recipe territory (docs/DECISIONS.md D25, Fork 2 resolved: accept Vorkath's wide
	 * aspect): grown from 110 to 140 so a tall animation's above-ground bounds — which the engine
	 * re-centres on the box every frame — has enough vertical room that a wide boss's zoom doesn't
	 * have to be pulled out so far it reads as a smear.
	 */
	static final int MODEL_HEIGHT = 140;

	private static final int MODEL_FILL = 0x0E0E0C;
	private static final int MODEL_BORDER = 0x474645;

	/** {@link IntUnaryOperator#applyAsInt} result meaning "no model resolved for this npc". */
	private static final int UNKNOWN_MODEL = -1;

	private static final int NAME_Y = 144;
	private static final int NAME_HEIGHT = 15;
	private static final int DESCRIPTION_Y = 161;
	private static final int DESCRIPTION_HEIGHT = 36;
	static final int COUNTERPLAY_Y = 199;
	static final int COUNTERPLAY_HEIGHT = 36;
	private static final int LINE_HEIGHT = 12;

	/**
	 * Script 4808's own idiom for a locked Combat Achievements entry: a black fill at
	 * {@code cc_settrans 150} over the whole panel. RuneLite opacity is INVERTED, so 150 is
	 * translucent, not nearly-solid.
	 */
	private static final int DIM_OPACITY = 150;

	private final Widget column;
	private final IntUnaryOperator modelForNpc;

	/** The LAYER every pool widget is created under (D19: nested dynamic children need a LAYER). */
	private Widget modelBox;

	/**
	 * One MODEL widget per distinct animation id ({@link PreviewSpec#NO_ANIMATION} included, for
	 * static-fallback poses), created lazily on first use. Never cleared out from under a live
	 * widget — see the class doc for why this map's lifetime is safe.
	 */
	private final Map<Integer, Widget> modelPool = new HashMap<>();

	/** The pool widget the previous {@link #show} left on screen, or null if none is. */
	private Widget visibleModel;

	private Widget name;
	private Widget description;
	private Widget counterplay;
	private Widget dim;

	/**
	 * @param column the 291-wide column layer, already positioned and sized
	 * @param modelForNpc resolves an npc id to the cache model id to render ({@link #UNKNOWN_MODEL}
	 *     if none); supplied as a lambda so this package never imports
	 *     {@code client.getNpcDefinition()}
	 */
	MechanicsDetail(Widget column, IntUnaryOperator modelForNpc)
	{
		this.column = column;
		this.modelForNpc = modelForNpc;
	}

	void build()
	{
		// A LAYER, not the RECTANGLE itself: the model needs a LAYER parent to render, since
		// nested dynamic children are only known to render under one (D19). The border is a
		// sibling drawn afterwards, so the model can never overdraw its own frame.
		int height = column.getOriginalHeight();
		modelBox = Widgets.layer(column, 0, 0, COLUMN_WIDTH, MODEL_HEIGHT);
		Widgets.filled(modelBox, 0, 0, COLUMN_WIDTH, MODEL_HEIGHT, MODEL_FILL);

		// Pool widgets are created lazily, per distinct animation id, the first time show() needs
		// one (D24) — not here. Creating one eagerly would mean an animation id of NO_ANIMATION
		// with no spec ever asking for it, which is harmless but pointless.

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
	 * widget and the column, which is what keeps whichever pool widget is currently visible
	 * playing without a rebuild.
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
	 * Gets or creates the pool widget for this spec's animation id (D24) and mutates only
	 * {@code setModelId}/{@code setModelZoom}/{@code setHidden}/the rect (below) on it — never
	 * {@code setAnimationId}, which is set exactly once, at creation, in {@link #poolWidgetFor}.
	 * Hides whatever pool widget was previously visible first, so at most one is ever shown at a
	 * time; hides outright for a locked row or an npc this client has no model for, so a locked
	 * mechanic can never leak through the preview.
	 *
	 * <p><b>The vertical anchor correction (docs/DECISIONS.md D26).</b> The engine anchors an if3
	 * MODEL widget's ground line (y=0) at the widget's own vertical centre, body extending
	 * upward — not the centre of its animated bounds, which D25 assumed. A curated
	 * {@code shiftY} moves that centre down by growing the widget's rect downward:
	 * {@code setOriginalHeight(MODEL_HEIGHT + 2*shiftY)} with {@code setOriginalY} pinned at 0, so
	 * the extra height only ever extends past the box's own bottom edge, never its top. This is
	 * mutated every {@code show()} rather than only at pool-widget creation, because two specs
	 * sharing one animation id (and so one pool widget) could in principle curate different
	 * {@code shiftY} values.
	 */
	private void showModel(PreviewSpec preview)
	{
		// Hidden specs carry a sentinel npc id, so resolving a model for one is at best a wasted
		// cache lookup and at worst a spurious no-model warning. Hide whatever was visible and stop.
		if (!preview.isVisible())
		{
			hideVisibleModel();
			return;
		}

		Widget widget = poolWidgetFor(preview.getAnimationId());

		if (visibleModel != null && visibleModel != widget)
		{
			visibleModel.setHidden(true);
			visibleModel.revalidate();
		}

		int modelId = modelForNpc.applyAsInt(preview.getNpcId());

		widget.setModelId(modelId);
		widget.setModelZoom(preview.getZoom());
		widget.setOriginalY(0);
		widget.setOriginalHeight(MODEL_HEIGHT + 2 * preview.getShiftY());
		widget.setHidden(modelId == UNKNOWN_MODEL);
		widget.revalidate();

		visibleModel = widget;
	}

	private void hideVisibleModel()
	{
		if (visibleModel != null)
		{
			visibleModel.setHidden(true);
			visibleModel.revalidate();
			visibleModel = null;
		}
	}

	/**
	 * The pool entry for {@code animationId}, creating it on first use. A freshly created MODEL
	 * widget's frame counter starts at zero (D24), so {@code setAnimationId} is called here, once,
	 * and never again for this widget's lifetime — that invariant is the entire fix.
	 */
	private Widget poolWidgetFor(int animationId)
	{
		return modelPool.computeIfAbsent(animationId, this::createPoolWidget);
	}

	private Widget createPoolWidget(int animationId)
	{
		Widget widget = modelBox.createChild(-1, WidgetType.MODEL);
		widget.setModelType(WidgetModelType.MODEL);
		// Rotation is fixed at what the spike validated (docs/DECISIONS.md D14); both axes must
		// stay within 0-2047 or the client crashes, which a constant zero trivially satisfies.
		widget.setRotationX(0);
		widget.setRotationY(0);
		widget.setRotationZ(0);
		widget.setOriginalX(0);
		widget.setOriginalY(0);
		widget.setOriginalWidth(COLUMN_WIDTH);
		widget.setOriginalHeight(MODEL_HEIGHT);
		widget.setAnimationId(animationId);
		widget.revalidate();
		return widget;
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
}

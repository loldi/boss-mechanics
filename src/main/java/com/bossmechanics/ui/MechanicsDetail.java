package com.bossmechanics.ui;

import com.bossmechanics.view.LineWrap;
import com.bossmechanics.view.MechanicRow;
import com.bossmechanics.view.PreviewSpec;
import com.bossmechanics.view.SecondaryPreviewSpec;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.api.widgets.WidgetPositionMode;
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
 * <p><b>The text block lives in a real scroll box, stacked by wrapped height (docs/DECISIONS.md
 * D28).</b> A long counterplay used to clip against a fixed-height box (issue #47's "Tentacle
 * Guard" case). {@link #show} now measures each of the three text widgets' own wrapped line count
 * ({@link LineWrap}, fed the widget's real font metrics) and stacks them one after another inside
 * a scrollable content layer, handing the resulting total height to a {@link MechanicsScrollbar}
 * whose bar hides itself whenever that height already fits the viewport.
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

	/**
	 * The model box's backdrop, measured from 713's statics: sprite 1040 tiled at opacity 40, in
	 * place of a flat fill. RuneLite opacity is inverted, so 40 is mostly (not barely) opaque.
	 */
	private static final int MODEL_BACKDROP_SPRITE = 1040;
	private static final int MODEL_BACKDROP_OPACITY = 40;

	/** {@link IntUnaryOperator#applyAsInt} result meaning "no model resolved for this npc". */
	private static final int UNKNOWN_MODEL = -1;

	/**
	 * Sprite previews (docs/DECISIONS.md D27): a bundled PNG shown in place of a model, exactly
	 * filling the model box's own interior inside its 2px section border (287x136 = 291x140 minus
	 * a 2px inset on every edge).
	 */
	private static final int SPRITE_X = 2;
	private static final int SPRITE_Y = 2;
	private static final int SPRITE_WIDTH = COLUMN_WIDTH - (2 * SPRITE_X);
	private static final int SPRITE_HEIGHT = MODEL_HEIGHT - (2 * SPRITE_Y);

	/** {@link ToIntFunction#applyAsInt} result meaning "no sprite registered for this name". */
	private static final int UNKNOWN_SPRITE = -1;

	/**
	 * G1 fix (docs/DECISIONS.md D27): the text area's own section border starts here, one pixel
	 * above the old {@code NAME_Y}, so the name's top row of glyphs no longer shares a scanline
	 * with the border's top edge.
	 */
	private static final int TEXT_AREA_Y = 140;
	private static final int TEXT_AREA_HEIGHT = COLUMN_HEIGHT - TEXT_AREA_Y;

	/**
	 * The section border draws a 2px frame ({@code Widgets.sectionBorder}: a 1px outer line then a
	 * 1px inner line one pixel in), so the real usable interior is 2px tighter on every edge than
	 * the raw {@link #TEXT_AREA_Y}/{@link #TEXT_AREA_HEIGHT} box.
	 */
	private static final int TEXT_AREA_INTERIOR_BOTTOM = TEXT_AREA_Y + TEXT_AREA_HEIGHT - 2;

	/** G1 fix (docs/DECISIONS.md D27): every text widget is inset this far from the border. */
	private static final int TEXT_X = 4;

	/**
	 * The scrollable text content's own top and height (docs/DECISIONS.md D28): the same starting
	 * row the name always had, running to the border's interior bottom -- a roughly 90px-tall
	 * viewport, visually the same box as before, just scrollable now instead of clipping.
	 */
	private static final int TEXT_CONTENT_Y = TEXT_AREA_Y + 3;
	private static final int TEXT_CONTENT_HEIGHT = TEXT_AREA_INTERIOR_BOTTOM - TEXT_CONTENT_Y;

	/**
	 * Reserves {@link MechanicsScrollbar#WIDTH} on the right so text wrapping is stable whether or
	 * not the bar ends up showing (docs/DECISIONS.md D28) -- wrapping against a width that changes
	 * depending on the very thing it is computing would be circular.
	 */
	private static final int TEXT_WIDTH = COLUMN_WIDTH - (2 * TEXT_X) - MechanicsScrollbar.WIDTH;

	/** Vertical breathing room between the stacked name/description/counterplay blocks. */
	private static final int TEXT_LINE_GAP = 2;

	private static final int LINE_HEIGHT = 12;

	/**
	 * Script 4808's own idiom for a locked Combat Achievements entry: a black fill at
	 * {@code cc_settrans 150} over the whole panel. RuneLite opacity is INVERTED, so 150 is
	 * translucent, not nearly-solid.
	 */
	private static final int DIM_OPACITY = 150;

	private final Widget column;
	private final IntUnaryOperator modelForNpc;
	private final ToIntFunction<String> spriteIdForName;

	/** The LAYER every pool widget is created under (D19: nested dynamic children need a LAYER). */
	private Widget modelBox;

	/**
	 * The primary preview's MODEL-widget slot (docs/DECISIONS.md D24). {@link #showModel} mutates
	 * only this and {@link #secondaryModel}; both share the pool-plus-visible shape in
	 * {@link ModelSlot} rather than duplicating it.
	 */
	private final ModelSlot primaryModel = new ModelSlot();

	/**
	 * A second, independent MODEL-widget slot for {@link PreviewSpec#getSecondary()} (docs/
	 * DECISIONS.md D27, secondary models, shape (a)) — its own pool, so the primary and secondary
	 * previews never share a widget even when they curate the same animation id.
	 */
	private final ModelSlot secondaryModel = new ModelSlot();

	/**
	 * One GRAPHIC widget per distinct resolved sprite id (docs/DECISIONS.md D27), created lazily
	 * on first use, mirroring the model pool's discipline: {@code setSpriteId} is called once, at
	 * creation, and never again. GRAPHIC widgets carry no frame counter, so D24's ban doesn't
	 * technically bind here, but keeping the same set-once shape avoids a second pattern to reason
	 * about for what is otherwise the model pool's twin.
	 */
	private final Map<Integer, Widget> spritePool = new HashMap<>();

	/** The sprite pool widget the previous {@link #show} left on screen, or null if none is. */
	private Widget visibleSprite;

	/** The scrollable content layer name/description/counterplay stack inside (docs/DECISIONS.md D28). */
	private Widget textContent;
	private MechanicsScrollbar textScrollbar;

	private Widget name;
	private Widget description;
	private Widget counterplay;
	private Widget dim;

	/**
	 * @param column the 291-wide column layer, already positioned and sized
	 * @param modelForNpc resolves an npc id to the cache model id to render ({@link #UNKNOWN_MODEL}
	 *     if none); supplied as a lambda so this package never imports
	 *     {@code client.getNpcDefinition()}
	 * @param spriteIdForName resolves a bundled sprite resource name to its registered (negative)
	 *     sprite id ({@link #UNKNOWN_SPRITE} if none); supplied as a lambda so this package never
	 *     imports {@code ImageUtil} or {@code client.getSpriteOverrides()} (docs/DECISIONS.md D27)
	 */
	MechanicsDetail(Widget column, IntUnaryOperator modelForNpc, ToIntFunction<String> spriteIdForName)
	{
		this.column = column;
		this.modelForNpc = modelForNpc;
		this.spriteIdForName = spriteIdForName;
	}

	void build()
	{
		// A LAYER, not the RECTANGLE itself: the model needs a LAYER parent to render, since
		// nested dynamic children are only known to render under one (D19). The border is a
		// sibling drawn afterwards, so the model can never overdraw its own frame.
		int height = column.getOriginalHeight();
		modelBox = Widgets.layer(column, 0, 0, COLUMN_WIDTH, MODEL_HEIGHT);
		Widgets.sprite(modelBox, MODEL_BACKDROP_SPRITE, 0, 0, COLUMN_WIDTH, MODEL_HEIGHT, true,
			MODEL_BACKDROP_OPACITY);

		// Pool widgets are created lazily, per distinct animation id, the first time show() needs
		// one (D24) — not here. Creating one eagerly would mean an animation id of NO_ANIMATION
		// with no spec ever asking for it, which is harmless but pointless.

		// The Combat Achievements section frame (docs/DECISIONS.md D26), a sibling drawn after the
		// box so the model can never overdraw its own frame.
		Widgets.sectionBorder(column, 0, 0, COLUMN_WIDTH, MODEL_HEIGHT);

		// The scrollable text content and its bar (docs/DECISIONS.md D28), the same shape
		// MechanicsList already uses for the row list: a viewport LAYER plus a scrollbar built
		// once here and only ever re-bound afterward via setContentHeight (never rebuilt).
		textContent = Widgets.layer(column, TEXT_X, TEXT_CONTENT_Y, TEXT_WIDTH, TEXT_CONTENT_HEIGHT);

		Widget textBar = Widgets.layer(column, TEXT_X, TEXT_CONTENT_Y,
			MechanicsScrollbar.WIDTH, TEXT_CONTENT_HEIGHT);
		textBar.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		textBar.revalidate();

		name = text(FontID.BOLD_12, Widgets.ORANGE);
		description = text(FontID.PLAIN_12, Widgets.WHITE);
		counterplay = text(FontID.PLAIN_12, Widgets.ORANGE);

		textScrollbar = new MechanicsScrollbar(textContent, textBar,
			TEXT_CONTENT_HEIGHT, TEXT_CONTENT_HEIGHT, TEXT_CONTENT_HEIGHT);
		textScrollbar.build();
		textScrollbar.listenForWheel(textContent);

		// Same section frame around the text block, drawn after its own text for the same reason.
		Widgets.sectionBorder(column, 0, TEXT_AREA_Y, COLUMN_WIDTH, TEXT_AREA_HEIGHT);

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
		// A locked row still fills the panel with "???" and empty strings, so the stack never
		// overlaps; the dim is what says "you have not found this yet".
		restackText(row == null ? "" : row.getName(),
			row == null ? "" : row.getDescription(),
			row == null ? "" : row.getCounterplay());

		PreviewSpec preview = row == null ? PreviewSpec.hidden() : row.getPreview();

		// Sprite tier (docs/DECISIONS.md D27): wins over the model slot entirely. Only one of the
		// two is ever shown, so switching between them hides whichever one the new spec doesn't use.
		if (preview.getSprite() != null)
		{
			hideVisibleModel();
			showSprite(preview.getSprite());
		}
		else
		{
			hideVisibleSprite();
			showModel(preview);
		}

		dim.setHidden(row == null || !row.isLocked());
		dim.revalidate();

		column.revalidate();
	}

	/**
	 * Shows the primary model, plus the secondary one if the spec curates one (docs/DECISIONS.md
	 * D27, shape (a)); hides both for a hidden spec. Hidden specs carry a sentinel npc id, so
	 * resolving a model for one is at best a wasted cache lookup and at worst a spurious
	 * no-model warning, hence the early return.
	 */
	private void showModel(PreviewSpec preview)
	{
		if (!preview.isVisible())
		{
			primaryModel.hide();
			secondaryModel.hide();
			return;
		}

		primaryModel.show(preview.getAnimationId(), preview.getModelId(), preview.getNpcId(),
			preview.getZoom(), preview.getShiftX(), preview.getShiftY(),
			preview.getRotationX(), preview.getRotationY(), preview.getRotationZ());

		SecondaryPreviewSpec secondary = preview.getSecondary();
		if (secondary == null)
		{
			secondaryModel.hide();
		}
		else
		{
			secondaryModel.show(secondary.getAnimationId(), secondary.getModelId(), secondary.getNpcId(),
				secondary.getZoom(), secondary.getShiftX(), secondary.getShiftY(),
				secondary.getRotationX(), secondary.getRotationY(), secondary.getRotationZ());
		}
	}

	private void hideVisibleModel()
	{
		primaryModel.hide();
		secondaryModel.hide();
	}

	/**
	 * One MODEL-widget slot in the model box: a pool keyed by animation id (docs/DECISIONS.md
	 * D24), plus which pool widget is currently visible. Used identically for the primary and
	 * secondary (D27) previews — both are "a MODEL widget resolved from either an explicit modelId
	 * or an npc lookup, with its own zoom and shiftX/shiftY rect" — so this is the one place that
	 * logic lives, rather than two near-identical copies.
	 */
	private final class ModelSlot
	{
		/**
		 * One MODEL widget per distinct animation id ({@link PreviewSpec#NO_ANIMATION} included,
		 * for static-fallback poses), created lazily on first use. Never cleared out from under a
		 * live widget — see the class doc for why this map's lifetime is safe.
		 */
		private final Map<Integer, Widget> pool = new HashMap<>();

		/** The pool widget the previous {@link #show} left on screen, or null if none is. */
		private Widget visible;

		/** Hides whatever is visible in this slot, for a hidden spec or an absent secondary. */
		void hide()
		{
			if (visible != null)
			{
				visible.setHidden(true);
				visible.revalidate();
				visible = null;
			}
		}

		/**
		 * Gets or creates the pool widget for {@code animationId} (D24) and mutates only
		 * {@code setModelId}/{@code setModelZoom}/{@code setHidden}/the rect on it afterward —
		 * never {@code setAnimationId} again after creation, in {@link #create}. Hides whatever
		 * was previously visible in this slot first, so at most one widget per slot is ever shown;
		 * hides outright when the resolved model id is unknown, so a locked mechanic (which never
		 * reaches here at all) or a model-less npc can never leak through the preview.
		 *
		 * <p><b>The {@code modelId} override (docs/DECISIONS.md D27).</b> When
		 * {@code explicitModelId} is present it is used directly and {@link #modelForNpc} is never
		 * called at all — a base spotanim model or a secondary model has no npc to look it up from.
		 *
		 * <p><b>The shiftX/shiftY anchor correction (docs/DECISIONS.md D26/D27).</b> The engine
		 * anchors an if3 MODEL widget's ground line at the widget's own centre (vertically) and its
		 * own centre (horizontally) too, so growing the rect by twice the (absolute) shift and
		 * offsetting its origin moves that centre without ever losing coverage of the box:
		 * {@code setOriginalX(shiftX - |shiftX|)}, {@code setOriginalWidth(COLUMN_WIDTH +
		 * 2*|shiftX|)} horizontally; {@code setOriginalY(0)}, {@code setOriginalHeight(MODEL_HEIGHT
		 * + 2*shiftY)} vertically, since a model only ever needs to move down from its own ground
		 * line, never up. Mutated every {@code show()} rather than only at creation, because two
		 * specs sharing one animation id (and so one pool widget) could curate different shifts.
		 *
		 * <p><b>Per-preview rotation (docs/DECISIONS.md D28).</b> D24 fixed rotation at a constant
		 * 0/0/0 in {@link #create}, the value the issue #1 spike validated; curated rotation is now
		 * a rect-style mutation applied here on every {@code show()} instead, the same register as
		 * shiftX/shiftY -- {@code setAnimationId} remains the only mutation D24 forbids on a live
		 * pool widget, and rotation is not it. Values are validated 0-2047 at curation time
		 * ({@code BossDataValidator}), so an out-of-range value that would crash the client (D23)
		 * never reaches this call.
		 */
		void show(int animationId, Integer explicitModelId, int npcId, int zoom, int shiftX, int shiftY,
			int rotationX, int rotationY, int rotationZ)
		{
			Widget widget = pool.computeIfAbsent(animationId, this::create);

			if (visible != null && visible != widget)
			{
				visible.setHidden(true);
				visible.revalidate();
			}

			int modelId = explicitModelId != null ? explicitModelId : modelForNpc.applyAsInt(npcId);
			int absShiftX = Math.abs(shiftX);

			widget.setModelId(modelId);
			widget.setModelZoom(zoom);
			widget.setRotationX(rotationX);
			widget.setRotationY(rotationY);
			widget.setRotationZ(rotationZ);
			widget.setOriginalX(shiftX - absShiftX);
			widget.setOriginalWidth(COLUMN_WIDTH + (2 * absShiftX));
			widget.setOriginalY(0);
			widget.setOriginalHeight(MODEL_HEIGHT + (2 * shiftY));
			widget.setHidden(modelId == UNKNOWN_MODEL);
			widget.revalidate();

			visible = widget;
		}

		/**
		 * A freshly created MODEL widget's frame counter starts at zero (D24), so
		 * {@code setAnimationId} is called here, once, and never again for this widget's
		 * lifetime — that invariant is the entire fix. Rotation is no longer set here (docs/
		 * DECISIONS.md D28): it moved to {@link #show}, since it is a per-preview curated value,
		 * not a constant.
		 */
		private Widget create(int animationId)
		{
			Widget widget = modelBox.createChild(-1, WidgetType.MODEL);
			widget.setModelType(WidgetModelType.MODEL);
			widget.setOriginalX(0);
			widget.setOriginalY(0);
			widget.setOriginalWidth(COLUMN_WIDTH);
			widget.setOriginalHeight(MODEL_HEIGHT);
			widget.setAnimationId(animationId);
			widget.revalidate();
			return widget;
		}
	}

	/**
	 * Gets or creates the sprite pool entry for {@code spriteName}'s resolved id and shows it,
	 * hiding whatever sprite widget was previously visible first (mirroring {@link #showModel}).
	 * An unknown name resolves to {@link #UNKNOWN_SPRITE}, which hides the widget instead of
	 * drawing garbage — the same sentinel-hides idiom {@link #showModel} uses for
	 * {@link #UNKNOWN_MODEL} (docs/DECISIONS.md D27).
	 */
	private void showSprite(String spriteName)
	{
		int spriteId = spriteIdForName.applyAsInt(spriteName);
		Widget widget = spritePool.computeIfAbsent(spriteId, this::createSpriteWidget);

		if (visibleSprite != null && visibleSprite != widget)
		{
			visibleSprite.setHidden(true);
			visibleSprite.revalidate();
		}

		widget.setHidden(spriteId == UNKNOWN_SPRITE);
		widget.revalidate();

		visibleSprite = widget;
	}

	private void hideVisibleSprite()
	{
		if (visibleSprite != null)
		{
			visibleSprite.setHidden(true);
			visibleSprite.revalidate();
			visibleSprite = null;
		}
	}

	/**
	 * The pool entry for {@code spriteId}, creating it on first use. {@code setSpriteId} is called
	 * here, once, mirroring the model pool's set-once discipline (D24) even though a GRAPHIC
	 * widget carries no frame counter to corrupt.
	 */
	private Widget createSpriteWidget(int spriteId)
	{
		Widget widget = modelBox.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalX(SPRITE_X);
		widget.setOriginalY(SPRITE_Y);
		widget.setOriginalWidth(SPRITE_WIDTH);
		widget.setOriginalHeight(SPRITE_HEIGHT);
		widget.setSpriteTiling(false);
		widget.setSpriteId(spriteId);
		widget.revalidate();
		return widget;
	}

	/**
	 * Restacks the three text widgets top to bottom by their own real wrapped height (docs/
	 * DECISIONS.md D28), then hands the total to {@link #textScrollbar} -- the mechanism that
	 * replaces the old fixed-height boxes a long counterplay used to clip against.
	 */
	private void restackText(String nameText, String descriptionText, String counterplayText)
	{
		int y = stack(name, nameText, 0) + TEXT_LINE_GAP;
		y = stack(description, descriptionText, y) + TEXT_LINE_GAP;
		int contentHeight = stack(counterplay, counterplayText, y);

		textScrollbar.setContentHeight(contentHeight);
	}

	/**
	 * Sets {@code widget}'s text, sizes it to its own wrapped line count at {@link #TEXT_WIDTH},
	 * positions it at {@code y}, and returns the y its bottom edge lands on -- what the next block
	 * in the stack (or the final content height) starts from.
	 */
	private int stack(Widget widget, String content, int y)
	{
		widget.setText(content);

		int height = LINE_HEIGHT * lineCount(widget, content);
		widget.setOriginalY(y);
		widget.setOriginalHeight(height);
		widget.revalidate();

		return y + height;
	}

	/**
	 * How many lines {@code content} wraps to at {@link #TEXT_WIDTH}, using the widget's own real
	 * font metrics ({@link LineWrap}). A null font (never expected once {@code build()} has set
	 * one, but a real possibility in a Proxy-backed test) falls back to one line rather than
	 * crashing -- the same null-font fallback {@code MechanicsList.fitName} already uses.
	 */
	private int lineCount(Widget widget, String content)
	{
		FontTypeFace font = widget.getFont();
		return font == null ? 1 : LineWrap.lines(content, font::getTextWidth, TEXT_WIDTH);
	}

	private Widget text(int fontId, int color)
	{
		Widget widget = Widgets.text(textContent, "", fontId, color);
		widget.setOriginalX(0);
		widget.setOriginalWidth(TEXT_WIDTH);
		widget.setLineHeight(LINE_HEIGHT);
		widget.revalidate();
		return widget;
	}
}

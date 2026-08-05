package com.bossmechanics.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bossmechanics.data.ChainSegment;
import com.bossmechanics.view.AnimationChain;
import com.bossmechanics.view.MechanicRow;
import com.bossmechanics.view.PreviewSpec;
import com.bossmechanics.view.SecondaryPreviewSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

/**
 * The animated preview (issue #6): {@link MechanicsDetail} owns a pool of {@code MODEL} widgets
 * keyed by animation id (docs/DECISIONS.md D24), rather than one widget mutated across every
 * animation. Both invariants below are single-assertion, Proxy-backed, in the style of
 * {@code MechanicsScrollbarTest} and {@code BossMechanicsWindowLayoutTest} (D22): neither depends
 * on real widget geometry, only on which methods get called and with what.
 */
public class MechanicsDetailPreviewTest
{
	@Test
	public void aModelWidgetNeverPlaysTwoDifferentAnimationsAcrossItsLifetime()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		// A long animation, played a while, then a shorter one: the crash pair family from the
		// in-game pass of #6 (issue #43) — e.g. minion-surge (60 frames) then miasma-pools
		// (30 frames). The engine invariant this protects: a MODEL widget's frame counter is only
		// ever zeroed by createChild, so swapping the sequence on a live widget corrupts it.
		detail.show(unlockedRow("m1", 1, 500));
		detail.show(unlockedRow("m2", 2, 600));

		assertEquals("a MODEL widget must play exactly one sequence for its whole lifetime "
				+ "(docs/DECISIONS.md D24): setAnimationId never resets the client's per-widget "
				+ "frame counter, only widget creation does, so no widget in the tree may ever "
				+ "receive setAnimationId with two different values",
			1, maxDistinctAnimationIdsEverSetOnAnyWidget(column));
	}

	@Test
	public void reselectingAMechanicWhoseAnimationWasAlreadyShownCreatesNoNewChildren()
	{
		List<String> calls = new ArrayList<>();
		Widget column = RecordingWidget.create(calls);

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		detail.show(unlockedRow("m1", 1, 500));
		detail.show(unlockedRow("m2", 2, 600));
		long createChildCallsSoFar = countCreateChild(calls);

		// Same animation id as the first show: the pool already holds a widget for it, so
		// re-selecting it (a repeat click, or cycling back around) must reuse that widget rather
		// than growing the pool without bound.
		detail.show(unlockedRow("m3", 3, 500));

		assertEquals("re-showing an animation already in the pool must reuse its widget, never "
				+ "create a new one (issue #6 crash fix, docs/DECISIONS.md D24)",
			createChildCallsSoFar, countCreateChild(calls));
	}

	@Test
	public void lockedRowHidesTheModelWidget()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		// An unlocked row first, so the model widget is identifiable below by its setModelId
		// call; a locked spec never resolves a model at all, it only hides.
		detail.show(unlockedRow("m1", 1, 500));
		detail.show(lockedRow());

		Widget model = findModelWidget(column);
		assertEquals("a locked row must hide the model widget so nothing spoils through it (issue #6)",
			Boolean.TRUE, RecordingWidget.lastArgsOf(model, "setHidden")[0]);
	}

	/**
	 * The text scroll box (docs/DECISIONS.md D28, issue #47 follow-up): a long counterplay used to
	 * clip against a fixed-height box (the "Tentacle Guard" case). Name/description/counterplay
	 * now stack by their own real wrapped height instead, so this replaces the old fixed-geometry
	 * assertion with the invariants that actually matter: the blocks never overlap, and the
	 * scrollable content height the scrollbar is told about matches the real stacked height.
	 */
	@Test
	public void textBlocksStackWithoutOverlappingAndScrollContentMatchesTheStackedHeight()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		detail.show(unlockedRowWithText("m1", "Tentacle Guard",
			"Tentacles ring the throne, shielding the respiratory systems.",
			"Stun the Sire to drop them for 30 seconds. Shadow Barrage always stuns."));

		Widget name = widgetWithText(column, "Tentacle Guard");
		Widget description = widgetWithText(column,
			"Tentacles ring the throne, shielding the respiratory systems.");
		Widget counterplay = widgetWithText(column,
			"Stun the Sire to drop them for 30 seconds. Shadow Barrage always stuns.");

		int nameBottom = originalY(name) + originalHeight(name);
		int descriptionY = originalY(description);
		int descriptionBottom = descriptionY + originalHeight(description);
		int counterplayY = originalY(counterplay);
		int counterplayBottom = counterplayY + originalHeight(counterplay);

		assertTrue("the description must start at or after the name block's own bottom edge",
			descriptionY >= nameBottom);
		assertTrue("the counterplay must start at or after the description block's own bottom edge",
			counterplayY >= descriptionBottom);

		Widget scrollContent = widgetsThatCalled(column, "setScrollHeight").get(0);
		assertEquals("the scrollable content height handed to the scrollbar must equal the real "
				+ "stacked height (counterplay's own bottom edge) plus the font's own below-"
				+ "baseline pad (docs/DECISIONS.md D34), or the box either clips or over-scrolls",
			counterplayBottom + MechanicsDetail.TEXT_DESCENT,
			((Integer) RecordingWidget.lastArgsOf(scrollContent, "setScrollHeight")[0]).intValue());
	}

	/**
	 * Item 1's real cause (docs/DECISIONS.md D34): {@code restackText} used to hand the raw
	 * stacked height straight to {@link MechanicsScrollbar#setContentHeight}, with zero allowance
	 * for the font's own extent below the last baseline (descenders on g/y/p, plus
	 * {@code setTextShadowed}'s own 1px drop). Between blocks that overhang fell harmlessly into
	 * the 12px gap; the FINAL block's overhang had nowhere to go, so the viewport clipped it even
	 * at max scroll -- Tentacle Guard's last line in the issue #47 follow-up screenshot.
	 */
	@Test
	public void textScrollContentReservesTheFontsDescentBelowTheLastLine()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		detail.show(unlockedRow("m1", 1, 500));

		Widget scrollContent = widgetsThatCalled(column, "setScrollHeight").get(0);
		int scrollHeight =
			((Integer) RecordingWidget.lastArgsOf(scrollContent, "setScrollHeight")[0]).intValue();

		assertTrue("TEXT_DESCENT must be a positive pad, or the last line's descenders still clip",
			MechanicsDetail.TEXT_DESCENT > 0);
		// Proxy widgets return a null font, so name/description/counterplay are each one line
		// (12px), separated by two 12px gaps: 12 + 12 + 12 + 12 + 12 = 60, plus the descent pad.
		assertEquals("the scrollable content height must reserve TEXT_DESCENT below the stacked "
				+ "blocks' own bottom edge",
			60 + MechanicsDetail.TEXT_DESCENT, scrollHeight);
	}

	/**
	 * G1's fix (docs/DECISIONS.md D27) moved every text widget off x=0 so the section border never
	 * overdrew the first glyph columns. The text scroll box (D28) achieves the same inset one level
	 * up: the text widgets sit at x=0 inside their own scrollable content layer, and that layer
	 * itself is what starts inset from the border.
	 */
	@Test
	public void theScrollableTextContentLayerIsInsetFromTheBorder()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		List<Widget> textWidgets = widgetsThatCalled(column, "setLineHeight");
		assertEquals("expected exactly the three text widgets (name, description, counterplay), "
				+ "identified by their unique setLineHeight call",
			3, textWidgets.size());

		Widget textContent = parentOf(column, textWidgets.get(0));
		int x = ((Integer) RecordingWidget.lastArgsOf(textContent, "setOriginalX")[0]).intValue();
		assertTrue("the scrollable text content layer must not start at x=0, or the section "
				+ "border's left edge draws over its first glyph columns (docs/DECISIONS.md D27, "
				+ "G1 fix; D28 moved the inset from the text widgets to their shared parent)",
			x >= 4);
	}

	private static int originalY(Widget widget)
	{
		return ((Integer) RecordingWidget.lastArgsOf(widget, "setOriginalY")[0]).intValue();
	}

	private static int originalHeight(Widget widget)
	{
		return ((Integer) RecordingWidget.lastArgsOf(widget, "setOriginalHeight")[0]).intValue();
	}

	/** The text widget whose most recent {@code setText} call carries {@code text} exactly. */
	private static Widget widgetWithText(Widget column, String text)
	{
		for (Widget widget : widgetsThatCalled(column, "setText"))
		{
			Object[] args = RecordingWidget.lastArgsOf(widget, "setText");
			if (text.equals(args[0]))
			{
				return widget;
			}
		}
		return null;
	}

	/** Walks {@code root}'s subtree looking for whichever widget's own children include {@code target}. */
	private static Widget parentOf(Widget root, Widget target)
	{
		for (Widget child : RecordingWidget.childrenOf(root))
		{
			if (child == target)
			{
				return root;
			}
			Widget found = parentOf(child, target);
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	private static List<Widget> widgetsThatCalled(Widget widget, String methodName)
	{
		List<Widget> found = new ArrayList<>();
		if (RecordingWidget.callsOf(widget).contains(methodName))
		{
			found.add(widget);
		}
		for (Widget child : RecordingWidget.childrenOf(widget))
		{
			found.addAll(widgetsThatCalled(child, methodName));
		}
		return found;
	}

	/**
	 * The model box IS the frame the engine centres a MODEL widget's above-ground bounds inside,
	 * every frame (docs/DECISIONS.md D25) — a pool widget whose own size drifts from the box's
	 * would silently mis-centre, with no crash to catch it.
	 *
	 * <p>Amended for the vertical anchor fix (docs/DECISIONS.md D26): the engine actually anchors
	 * a MODEL widget's ground line at the widget's own vertical centre, not the centre of its
	 * animated bounds, so a curated {@code shiftY} grows the pool widget's rect downward
	 * ({@code setOriginalHeight = MODEL_HEIGHT + 2*shiftY}, {@code setOriginalY} unchanged at 0)
	 * to move that centre without tilting the model. Width is untouched by shiftY, so this still
	 * catches the same box-size drift the original test existed for.
	 */
	@Test
	public void modelPoolWidgetMatchesTheModelBoxSize()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		int shiftY = 30;
		detail.show(unlockedRow("m1", 1, 500, shiftY));

		Widget model = findModelWidget(column);
		assertEquals("a pool widget's width must match the model box it is centered inside",
			MechanicsDetail.COLUMN_WIDTH,
			((Integer) RecordingWidget.lastArgsOf(model, "setOriginalWidth")[0]).intValue());
		assertEquals("a pool widget's height must grow by twice the curated shiftY (docs/"
				+ "DECISIONS.md D26), which is what moves its vertical center down without tilting "
				+ "the model",
			MechanicsDetail.MODEL_HEIGHT + 2 * shiftY,
			((Integer) RecordingWidget.lastArgsOf(model, "setOriginalHeight")[0]).intValue());
		assertEquals("a pool widget's y origin stays 0: the rect grows only downward from the "
				+ "box's own top edge (docs/DECISIONS.md D26)",
			0,
			((Integer) RecordingWidget.lastArgsOf(model, "setOriginalY")[0]).intValue());
	}

	/**
	 * {@code preview.modelId} override (docs/DECISIONS.md D27): an explicit model id must be used
	 * directly on the pool widget and must skip the npc -> model lookup lambda entirely, since
	 * secondary models (and other non-npc-derived models) have no npc to resolve.
	 */
	@Test
	public void explicitModelIdSkipsNpcResolution()
	{
		Widget column = RecordingWidget.create();
		int[] modelForNpcCalls = new int[1];

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> {
			modelForNpcCalls[0]++;
			return npcId * 10;
		}, name -> -1, () -> null);
		detail.build();

		detail.show(unlockedRowWithModelId("m1", 17550, 500));

		Widget model = findModelWidget(column);
		assertEquals("an explicit preview.modelId must be set directly on the pool widget",
			17550, ((Integer) RecordingWidget.lastArgsOf(model, "setModelId")[0]).intValue());
		assertEquals("an explicit preview.modelId must skip the npc->model lookup lambda entirely",
			0, modelForNpcCalls[0]);
	}

	/**
	 * The horizontal anchor correction (docs/DECISIONS.md D27) mirrors shiftY's mechanism
	 * sideways: width grows by twice the absolute shiftX and x moves the same amount, so the rect
	 * always still covers the box while its centre moves off to one side.
	 */
	@Test
	public void primaryModelWidgetRectReflectsShiftX()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		int shiftX = -50;
		detail.show(unlockedRow("m1", 1, 500, 0, shiftX));

		Widget model = findModelWidget(column);
		assertEquals("width must grow by twice the absolute shiftX",
			MechanicsDetail.COLUMN_WIDTH + (2 * Math.abs(shiftX)),
			((Integer) RecordingWidget.lastArgsOf(model, "setOriginalWidth")[0]).intValue());
		assertEquals("x must move by shiftX minus its own absolute value, so the rect still covers "
				+ "the box on the side shiftX moved away from",
			shiftX - Math.abs(shiftX),
			((Integer) RecordingWidget.lastArgsOf(model, "setOriginalX")[0]).intValue());
	}

	/**
	 * Per-preview rotation (docs/DECISIONS.md D28): a curated rotation is applied to the visible
	 * pool widget on every {@code show()}, mirroring how shiftX/shiftY are already rect-mutated
	 * rather than fixed at creation.
	 */
	@Test
	public void primaryModelWidgetReceivesTheSpecsRotation()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		detail.show(unlockedRowWithRotation("m1", 1, 500, 512, 1024, 1536));

		Widget model = findModelWidget(column);
		assertEquals(512, ((Integer) RecordingWidget.lastArgsOf(model, "setRotationX")[0]).intValue());
		assertEquals(1024, ((Integer) RecordingWidget.lastArgsOf(model, "setRotationY")[0]).intValue());
		assertEquals(1536, ((Integer) RecordingWidget.lastArgsOf(model, "setRotationZ")[0]).intValue());
	}

	/**
	 * Secondary models (docs/DECISIONS.md D27, shape (a)): a curated secondary is a second MODEL
	 * widget shown alongside the primary, resolved through its own modelId/npcId precedence.
	 */
	@Test
	public void secondaryModelCreatesASecondModelWidget()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		SecondaryPreviewSpec secondary = new SecondaryPreviewSpec(29475, 0, 7115, 1100, 90, 33, 0, 0, 0);
		detail.show(unlockedRowWithSecondary("m1", 1, 500, secondary));

		List<Widget> modelWidgets = widgetsThatCalled(column, "setModelId");
		assertEquals("expected two MODEL widgets: one for the primary preview, one for the secondary",
			2, modelWidgets.size());
		assertTrue("expected one of the two MODEL widgets to carry the secondary's explicit modelId",
			carriesModelId(modelWidgets, 29475));
	}

	/** docs/DECISIONS.md D27: re-showing a row whose secondary was already shown creates no new children. */
	@Test
	public void reselectingARowWithTheSameSecondaryCreatesNoNewChildren()
	{
		List<String> calls = new ArrayList<>();
		Widget column = RecordingWidget.create(calls);

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		SecondaryPreviewSpec secondary = new SecondaryPreviewSpec(29475, 0, 7115, 1100, 90, 33, 0, 0, 0);
		detail.show(unlockedRowWithSecondary("m1", 1, 500, secondary));
		long createChildCallsSoFar = countCreateChild(calls);

		detail.show(unlockedRowWithSecondary("m2", 1, 500, secondary));

		assertEquals("re-showing the same secondary animation must reuse its widget, never create "
				+ "a new one",
			createChildCallsSoFar, countCreateChild(calls));
	}

	/** docs/DECISIONS.md D27: a row without a secondary hides whichever one was previously visible. */
	@Test
	public void rowWithoutASecondaryHidesThePreviouslyVisibleOne()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		SecondaryPreviewSpec secondary = new SecondaryPreviewSpec(29475, 0, 7115, 1100, 90, 33, 0, 0, 0);
		detail.show(unlockedRowWithSecondary("m1", 1, 500, secondary));
		detail.show(unlockedRow("m2", 2, 600));

		Widget secondaryWidget = modelWidgetCarrying(column, 29475);
		assertEquals("a row without a secondary must hide whichever secondary widget was "
				+ "previously visible",
			Boolean.TRUE, RecordingWidget.lastArgsOf(secondaryWidget, "setHidden")[0]);
	}

	/**
	 * Chained preview animations (issue #66, docs/DECISIONS.md D37): {@link MechanicsDetail#tick()}
	 * walks a curated chain on the existing D24 pool, swapping which pool widget is visible only
	 * on a segment boundary -- never every tick, and never via {@code setAnimationId} on a live
	 * widget.
	 */
	@Test
	public void tickWithinASegmentMutatesNothing()
	{
		List<String> calls = new ArrayList<>();
		Widget column = RecordingWidget.create(calls);

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		AnimationChain chain = AnimationChain.of(Arrays.asList(
			new ChainSegment(500, 3), new ChainSegment(600, 2)));
		detail.show(unlockedRowWithChain("m1", 1, chain));
		int callsAfterShow = calls.size();

		// The first segment is 3 cycles long, so two ticks stay well inside it.
		detail.tick();
		detail.tick();

		assertEquals("ticking within a segment must touch no widget at all -- a boundary is the "
				+ "only thing tick() may act on",
			callsAfterShow, calls.size());
	}

	@Test
	public void tickAcrossABoundarySwapsThePoolWidgetWithoutTouchingSetAnimationId()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		AnimationChain chain = AnimationChain.of(Arrays.asList(
			new ChainSegment(500, 3), new ChainSegment(600, 2)));
		detail.show(unlockedRowWithChain("m1", 1, chain));

		detail.tick();
		detail.tick();
		assertEquals("still inside the first segment", 1, widgetsThatCalled(column, "setModelId").size());

		// The third tick lands exactly on the boundary (elapsed ticks == the first segment's own
		// cycles), which is when the chain's own resolved animation id first changes.
		detail.tick();

		List<Widget> modelWidgets = widgetsThatCalled(column, "setModelId");
		assertEquals("a boundary must get-or-create the pool widget for the new segment, exactly "
				+ "like an ordinary selection change (docs/DECISIONS.md D24)",
			2, modelWidgets.size());
		assertEquals("a MODEL widget must play exactly one sequence for its whole lifetime "
				+ "(docs/DECISIONS.md D24): the boundary swap must never call setAnimationId on "
				+ "the widget that was already showing",
			1, maxDistinctAnimationIdsEverSetOnAnyWidget(column));
	}

	@Test
	public void aFullPassWrapsAndReusesWithoutGrowingThePool()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		AnimationChain chain = AnimationChain.of(Arrays.asList(
			new ChainSegment(500, 2), new ChainSegment(600, 2), new ChainSegment(700, 2)));
		detail.show(unlockedRowWithChain("m1", 1, chain));

		// Two full passes (12 ticks: the chain's own 6-cycle length, twice) must still land back on
		// only the three widgets the chain's three distinct segments need -- the whole chain loops
		// as a unit (docs/DECISIONS.md D37) rather than growing a new widget every wrap.
		for (int i = 0; i < 12; i++)
		{
			detail.tick();
		}

		assertEquals("a chain that has wrapped around must still show only its own distinct "
				+ "segments' widgets, never grow the pool",
			3, widgetsThatCalled(column, "setModelId").size());
	}

	@Test
	public void switchingMechanicsMidChainRestartsTheNewSelectionAtSegmentZero()
	{
		List<String> calls = new ArrayList<>();
		Widget column = RecordingWidget.create(calls);

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		AnimationChain chainA = AnimationChain.of(Arrays.asList(
			new ChainSegment(500, 3), new ChainSegment(600, 2)));
		detail.show(unlockedRowWithChain("m1", 1, chainA));
		// Run chain A's playhead well past where a second chain's own first segment would end, so
		// a bug that failed to reset the playhead on the new selection would show up immediately.
		for (int i = 0; i < 12; i++)
		{
			detail.tick();
		}

		AnimationChain chainB = AnimationChain.of(Arrays.asList(
			new ChainSegment(700, 10), new ChainSegment(800, 5)));
		detail.show(unlockedRowWithChain("m2", 2, chainB));
		int callsAfterShow = calls.size();

		// One tick into the new selection: correctly restarted at segment 0, this is nowhere near
		// chain B's own 10-cycle first segment, so nothing may swap. A stale (un-reset) playhead
		// carried over from chain A's 12 ticks would already be past chain B's first boundary.
		detail.tick();

		assertEquals("selecting a new chained mechanic must restart its playhead at segment 0, not "
				+ "continue counting from the previous selection's chain",
			callsAfterShow, calls.size());
	}

	private static MechanicRow unlockedRowWithChain(String mechanicId, int npcId, AnimationChain chain)
	{
		return new MechanicRow(mechanicId, true, false, "Name", "Description", "Counterplay", null,
			new PreviewSpec(true, npcId, chain.animationAt(0), PreviewSpec.DEFAULT_ZOOM, 0, null, null, 0, null,
				0, 0, 0, chain));
	}

	private static boolean carriesModelId(List<Widget> widgets, int modelId)
	{
		for (Widget widget : widgets)
		{
			if (((Integer) RecordingWidget.lastArgsOf(widget, "setModelId")[0]).intValue() == modelId)
			{
				return true;
			}
		}
		return false;
	}

	private static Widget modelWidgetCarrying(Widget column, int modelId)
	{
		for (Widget widget : widgetsThatCalled(column, "setModelId"))
		{
			if (((Integer) RecordingWidget.lastArgsOf(widget, "setModelId")[0]).intValue() == modelId)
			{
				return widget;
			}
		}
		return null;
	}

	/**
	 * Sprite previews (docs/DECISIONS.md D27): a bundled image resolves to a GRAPHIC pool widget
	 * keyed by its resolved sprite id, and must never touch the npc -> model lookup lambda at all.
	 */
	@Test
	public void spritePreviewCreatesOneGraphicWidgetAndNeverInvokesModelForNpc()
	{
		Widget column = RecordingWidget.create();
		int[] modelForNpcCalls = new int[1];

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> {
			modelForNpcCalls[0]++;
			return npcId * 10;
		}, name -> -3517000, () -> null);
		detail.build();

		detail.show(spriteRow("m1", "venomous-dragonfire.png"));

		Widget sprite = findSpritePreviewWidget(column);
		assertEquals("expected the sprite preview widget to carry the resolved sprite id", -3517000,
			((Integer) RecordingWidget.lastArgsOf(sprite, "setSpriteId")[0]).intValue());
		assertEquals("a sprite spec must never invoke the npc->model lookup lambda",
			0, modelForNpcCalls[0]);
	}

	/** docs/DECISIONS.md D27: re-showing the same sprite must reuse its pool widget. */
	@Test
	public void reselectingTheSameSpriteCreatesNoNewChildren()
	{
		List<String> calls = new ArrayList<>();
		Widget column = RecordingWidget.create(calls);

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -3517000, () -> null);
		detail.build();

		detail.show(spriteRow("m1", "venomous-dragonfire.png"));
		long createChildCallsSoFar = countCreateChild(calls);

		detail.show(spriteRow("m2", "venomous-dragonfire.png"));

		assertEquals("re-showing the same sprite must reuse its widget, never create a new one",
			createChildCallsSoFar, countCreateChild(calls));
	}

	/** docs/DECISIONS.md D27: the sprite and model slots are mutually exclusive on screen. */
	@Test
	public void switchingFromSpriteToModelHidesTheSpriteWidget()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -3517000, () -> null);
		detail.build();

		detail.show(spriteRow("m1", "venomous-dragonfire.png"));
		detail.show(unlockedRow("m2", 1, 500));

		Widget sprite = findSpritePreviewWidget(column);
		assertEquals("switching to a model row must hide the previously visible sprite widget",
			Boolean.TRUE, RecordingWidget.lastArgsOf(sprite, "setHidden")[0]);
	}

	/**
	 * docs/DECISIONS.md D27: a sprite name with no registered id resolves to the unknown sentinel,
	 * which must hide the widget rather than draw sprite -1. Only reachable if a bundled resource
	 * fails to register at runtime, which no data test can catch.
	 */
	@Test
	public void anUnregisteredSpriteNameHidesTheSpriteWidget()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1, () -> null);
		detail.build();

		detail.show(spriteRow("m1", "never-registered.png"));

		Widget sprite = findSpritePreviewWidget(column);
		assertEquals("an unregistered sprite name must hide the widget, never draw the sentinel id",
			Boolean.TRUE, RecordingWidget.lastArgsOf(sprite, "setHidden")[0]);
	}

	/**
	 * The sprite preview widget is the only one in the tree that calls
	 * {@code setSpriteTiling(false)} -- the model box's own sprite-1040 backdrop (D26) also calls
	 * {@code setSpriteId}, but tiled ({@code true}), so a plain "who called setSpriteId" search
	 * finds the backdrop first.
	 */
	private static Widget findSpritePreviewWidget(Widget widget)
	{
		Object[] args = RecordingWidget.lastArgsOf(widget, "setSpriteTiling");
		if (args != null && args.length == 1 && Boolean.FALSE.equals(args[0]))
		{
			return widget;
		}
		for (Widget child : RecordingWidget.childrenOf(widget))
		{
			Widget found = findSpritePreviewWidget(child);
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	private static long countCreateChild(List<String> calls)
	{
		return calls.stream().filter("createChild"::equals).count();
	}

	/**
	 * Walks every widget under {@code roots} and, for each one, the distinct values ever passed to
	 * its {@code setAnimationId}, then returns the largest such count found anywhere in the tree.
	 * A correctly pooled implementation never exceeds 1 (each pool widget's animation id is fixed
	 * at creation); the pre-fix single-mutated-widget implementation hits 2 as soon as a second,
	 * different animation is shown.
	 */
	private static int maxDistinctAnimationIdsEverSetOnAnyWidget(Widget... roots)
	{
		int max = 0;
		for (Widget root : roots)
		{
			for (Widget widget : allWidgetsUnder(root))
			{
				Set<Object> distinctAnimationIds = new HashSet<>();
				for (Object[] args : RecordingWidget.allArgsOf(widget, "setAnimationId"))
				{
					distinctAnimationIds.add(args[0]);
				}
				max = Math.max(max, distinctAnimationIds.size());
			}
		}
		return max;
	}

	private static List<Widget> allWidgetsUnder(Widget widget)
	{
		List<Widget> all = new ArrayList<>();
		all.add(widget);
		for (Widget child : RecordingWidget.childrenOf(widget))
		{
			all.addAll(allWidgetsUnder(child));
		}
		return all;
	}

	/** The model widget is the only one in the tree that ever calls {@code setModelId}. */
	private static Widget findModelWidget(Widget widget)
	{
		if (RecordingWidget.callsOf(widget).contains("setModelId"))
		{
			return widget;
		}
		for (Widget child : RecordingWidget.childrenOf(widget))
		{
			Widget found = findModelWidget(child);
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	private static MechanicRow unlockedRow(String mechanicId, int npcId, int animationId)
	{
		return unlockedRow(mechanicId, npcId, animationId, 0);
	}

	private static MechanicRow unlockedRow(String mechanicId, int npcId, int animationId, int shiftY)
	{
		return unlockedRow(mechanicId, npcId, animationId, shiftY, 0);
	}

	private static MechanicRow unlockedRow(String mechanicId, int npcId, int animationId, int shiftY, int shiftX)
	{
		return new MechanicRow(mechanicId, true, false, "Name", "Description", "Counterplay", null,
			new PreviewSpec(true, npcId, animationId, PreviewSpec.DEFAULT_ZOOM, shiftY, null, null, shiftX, null, 0, 0, 0, null));
	}

	private static MechanicRow unlockedRowWithText(String mechanicId, String name, String description,
		String counterplay)
	{
		return new MechanicRow(mechanicId, true, false, name, description, counterplay, null,
			new PreviewSpec(true, 1, 500, PreviewSpec.DEFAULT_ZOOM, 0, null, null, 0, null, 0, 0, 0, null));
	}

	private static MechanicRow unlockedRowWithRotation(String mechanicId, int npcId, int animationId,
		int rotationX, int rotationY, int rotationZ)
	{
		return new MechanicRow(mechanicId, true, false, "Name", "Description", "Counterplay", null,
			new PreviewSpec(true, npcId, animationId, PreviewSpec.DEFAULT_ZOOM, 0, null, null, 0, null,
				rotationX, rotationY, rotationZ, null));
	}

	private static MechanicRow unlockedRowWithModelId(String mechanicId, int modelId, int animationId)
	{
		return new MechanicRow(mechanicId, true, false, "Name", "Description", "Counterplay", null,
			new PreviewSpec(true, 0, animationId, PreviewSpec.DEFAULT_ZOOM, 0, modelId, null, 0, null, 0, 0, 0, null));
	}

	private static MechanicRow spriteRow(String mechanicId, String sprite)
	{
		return new MechanicRow(mechanicId, true, false, "Name", "Description", "Counterplay", null,
			new PreviewSpec(true, 0, PreviewSpec.NO_ANIMATION, PreviewSpec.DEFAULT_ZOOM, 0, null, sprite, 0, null, 0, 0, 0, null));
	}

	private static MechanicRow unlockedRowWithSecondary(String mechanicId, int npcId, int animationId,
		SecondaryPreviewSpec secondary)
	{
		return new MechanicRow(mechanicId, true, false, "Name", "Description", "Counterplay", null,
			new PreviewSpec(true, npcId, animationId, PreviewSpec.DEFAULT_ZOOM, 0, null, null, 0, secondary, 0, 0, 0, null));
	}

	private static MechanicRow lockedRow()
	{
		return new MechanicRow("locked", false, true, "???", "", "", null, PreviewSpec.hidden());
	}
}

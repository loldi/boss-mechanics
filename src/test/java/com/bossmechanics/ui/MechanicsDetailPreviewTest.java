package com.bossmechanics.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bossmechanics.view.MechanicRow;
import com.bossmechanics.view.PreviewSpec;
import com.bossmechanics.view.SecondaryPreviewSpec;
import java.util.ArrayList;
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1);
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1);
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1);
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
	 * The enlarged model box (Fork 2, resolved: accept Vorkath's wide aspect, docs/DECISIONS.md
	 * D25) grew from 110 to 140 tall, which pushed name/description/counterplay down with it. This
	 * pins the new numbers against each other rather than the client: the text block must still
	 * fit inside the 235-tall column the window now hands this class, with nothing left over to
	 * silently clip.
	 *
	 * <p>Tightened for the G1 text-inset fix (docs/DECISIONS.md D27): the real constraint is the
	 * text area's own section-border <b>interior</b> (233), two pixels tighter than the column's
	 * raw height (235), since {@code Widgets.sectionBorder} draws a 2px frame around the block.
	 */
	@Test
	public void textBlockFitsInsideTheSectionBorderInterior()
	{
		assertTrue("the text block (name, description, counterplay) must end at or before the "
				+ "text area's own section-border interior, not just the column height, or the "
				+ "border draws over the last pixels of counterplay (docs/DECISIONS.md D27, G1)",
			MechanicsDetail.COUNTERPLAY_Y + MechanicsDetail.COUNTERPLAY_HEIGHT
				<= MechanicsDetail.TEXT_AREA_INTERIOR_BOTTOM);
	}

	/**
	 * G1 root cause (docs/DECISIONS.md D27): the name/description/counterplay text widgets used to
	 * start at x=0, so the text area's own section border (drawn after the text, at x=0 and x=1)
	 * overdrew the first two glyph columns of every line. Each text widget must now start inset
	 * from the border.
	 */
	@Test
	public void textWidgetsAreInsetFromTheBorder()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1);
		detail.build();

		List<Widget> textWidgets = widgetsThatCalled(column, "setLineHeight");
		assertEquals("expected exactly the three text widgets (name, description, counterplay), "
				+ "identified by their unique setLineHeight call",
			3, textWidgets.size());
		for (Widget widget : textWidgets)
		{
			int x = ((Integer) RecordingWidget.lastArgsOf(widget, "setOriginalX")[0]).intValue();
			assertTrue("a text widget must not start at x=0, or the section border's left edge "
					+ "draws over its first glyph columns (docs/DECISIONS.md D27, G1 fix)",
				x >= 4);
		}
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1);
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
		}, name -> -1);
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1);
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
	 * Secondary models (docs/DECISIONS.md D27, shape (a)): a curated secondary is a second MODEL
	 * widget shown alongside the primary, resolved through its own modelId/npcId precedence.
	 */
	@Test
	public void secondaryModelCreatesASecondModelWidget()
	{
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1);
		detail.build();

		SecondaryPreviewSpec secondary = new SecondaryPreviewSpec(29475, 0, 7115, 1100, 90, 33);
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1);
		detail.build();

		SecondaryPreviewSpec secondary = new SecondaryPreviewSpec(29475, 0, 7115, 1100, 90, 33);
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -1);
		detail.build();

		SecondaryPreviewSpec secondary = new SecondaryPreviewSpec(29475, 0, 7115, 1100, 90, 33);
		detail.show(unlockedRowWithSecondary("m1", 1, 500, secondary));
		detail.show(unlockedRow("m2", 2, 600));

		Widget secondaryWidget = modelWidgetCarrying(column, 29475);
		assertEquals("a row without a secondary must hide whichever secondary widget was "
				+ "previously visible",
			Boolean.TRUE, RecordingWidget.lastArgsOf(secondaryWidget, "setHidden")[0]);
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
		}, name -> -3517000);
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -3517000);
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10, name -> -3517000);
		detail.build();

		detail.show(spriteRow("m1", "venomous-dragonfire.png"));
		detail.show(unlockedRow("m2", 1, 500));

		Widget sprite = findSpritePreviewWidget(column);
		assertEquals("switching to a model row must hide the previously visible sprite widget",
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
			new PreviewSpec(true, npcId, animationId, PreviewSpec.DEFAULT_ZOOM, shiftY, null, null, shiftX, null));
	}

	private static MechanicRow unlockedRowWithModelId(String mechanicId, int modelId, int animationId)
	{
		return new MechanicRow(mechanicId, true, false, "Name", "Description", "Counterplay", null,
			new PreviewSpec(true, 0, animationId, PreviewSpec.DEFAULT_ZOOM, 0, modelId, null, 0, null));
	}

	private static MechanicRow spriteRow(String mechanicId, String sprite)
	{
		return new MechanicRow(mechanicId, true, false, "Name", "Description", "Counterplay", null,
			new PreviewSpec(true, 0, PreviewSpec.NO_ANIMATION, PreviewSpec.DEFAULT_ZOOM, 0, null, sprite, 0, null));
	}

	private static MechanicRow unlockedRowWithSecondary(String mechanicId, int npcId, int animationId,
		SecondaryPreviewSpec secondary)
	{
		return new MechanicRow(mechanicId, true, false, "Name", "Description", "Counterplay", null,
			new PreviewSpec(true, npcId, animationId, PreviewSpec.DEFAULT_ZOOM, 0, null, null, 0, secondary));
	}

	private static MechanicRow lockedRow()
	{
		return new MechanicRow("locked", false, true, "???", "", "", null, PreviewSpec.hidden());
	}
}

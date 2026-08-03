package com.bossmechanics.ui;

import static org.junit.Assert.assertEquals;

import com.bossmechanics.view.MechanicRow;
import com.bossmechanics.view.PreviewSpec;
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10);
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10);
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

		MechanicsDetail detail = new MechanicsDetail(column, npcId -> npcId * 10);
		detail.build();

		// An unlocked row first, so the model widget is identifiable below by its setModelId
		// call; a locked spec never resolves a model at all, it only hides.
		detail.show(unlockedRow("m1", 1, 500));
		detail.show(lockedRow());

		Widget model = findModelWidget(column);
		assertEquals("a locked row must hide the model widget so nothing spoils through it (issue #6)",
			Boolean.TRUE, RecordingWidget.lastArgsOf(model, "setHidden")[0]);
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
		return new MechanicRow(mechanicId, true, false, "Name", "Description", "Counterplay", null,
			new PreviewSpec(true, npcId, animationId, PreviewSpec.DEFAULT_ZOOM));
	}

	private static MechanicRow lockedRow()
	{
		return new MechanicRow("locked", false, true, "???", "", "", null, PreviewSpec.hidden());
	}
}

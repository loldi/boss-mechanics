package com.bossmechanics.ui;

import static org.junit.Assert.assertEquals;

import com.bossmechanics.view.MechanicRow;
import com.bossmechanics.view.PreviewSpec;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

/**
 * The animated preview (issue #6): {@link MechanicsDetail} owns one persistent {@code MODEL}
 * widget, created once in {@link MechanicsDetail#build()} and mutated on every
 * {@link MechanicsDetail#show}. Both invariants below are single-assertion, Proxy-backed, in the
 * style of {@code MechanicsScrollbarTest} and {@code BossMechanicsWindowLayoutTest} (D22): neither
 * depends on real widget geometry, only on which methods get called and with what.
 */
public class MechanicsDetailPreviewTest
{
	@Test
	public void selectingADifferentMechanicMutatesTheModelRatherThanRebuildingIt()
	{
		List<String> calls = new ArrayList<>();
		Widget header = RecordingWidget.create(calls);
		Widget column = RecordingWidget.create(calls);

		MechanicsDetail detail = new MechanicsDetail(header, column, npcId -> npcId * 10, () -> { });
		detail.build();
		long createChildCallsAfterBuild = countCreateChild(calls);

		detail.show(unlockedRow("m1", 1, 500));
		detail.show(unlockedRow("m2", 2, 600));

		assertEquals("show() must mutate the existing model widget, never create a new one (issue #6)",
			createChildCallsAfterBuild, countCreateChild(calls));
	}

	@Test
	public void lockedRowHidesTheModelWidget()
	{
		Widget header = RecordingWidget.create();
		Widget column = RecordingWidget.create();

		MechanicsDetail detail = new MechanicsDetail(header, column, npcId -> npcId * 10, () -> { });
		detail.build();

		detail.show(lockedRow());

		Widget model = findModelWidget(column);
		assertEquals("a locked row must hide the model widget so nothing spoils through it (issue #6)",
			Boolean.TRUE, RecordingWidget.lastArgsOf(model, "setHidden")[0]);
	}

	private static long countCreateChild(List<String> calls)
	{
		return calls.stream().filter("createChild"::equals).count();
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

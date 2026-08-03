package com.bossmechanics.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.bossmechanics.data.Mechanic;
import com.bossmechanics.data.Preview;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The resolution rules behind the animated preview (issue #6, docs/DECISIONS.md D23): what npc,
 * animation and zoom the model box should show for a mechanic, decided once here so
 * {@code com.bossmechanics.ui} only ever mutates a widget to match.
 */
public class PreviewSpecTest
{
	private static final List<Integer> BOSS_NPC_IDS = Collections.singletonList(111);

	@Test
	public void lockedIsNeverVisible()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, null, null, null)), BOSS_NPC_IDS, true);

		assertFalse(spec.isVisible());
	}

	@Test
	public void npcIdDefaultsToTheBossFirstNpcId()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, null, null, null)), BOSS_NPC_IDS, false);

		assertEquals(111, spec.getNpcId());
	}

	@Test
	public void explicitNpcIdIsHonored()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, 222, false, null, null, null)), BOSS_NPC_IDS, false);

		assertEquals(222, spec.getNpcId());
	}

	@Test
	public void staticFallbackWinsOverAPresentAnimationId()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, true, null, null, null)), BOSS_NPC_IDS, false);

		assertEquals(PreviewSpec.NO_ANIMATION, spec.getAnimationId());
	}

	@Test
	public void missingAnimationIdFallsBackToAStaticPoseInsteadOfCrashing()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(null, null, false, null, null, null)), BOSS_NPC_IDS, false);

		assertEquals(PreviewSpec.NO_ANIMATION, spec.getAnimationId());
	}

	@Test
	public void zoomDefaultsTo3000()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, null, null, null)), BOSS_NPC_IDS, false);

		assertEquals(PreviewSpec.DEFAULT_ZOOM, spec.getZoom());
	}

	@Test
	public void explicitZoomIsHonored()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, 1500, null, null)), BOSS_NPC_IDS, false);

		assertEquals(1500, spec.getZoom());
	}

	/** docs/DECISIONS.md D26: an absent shiftY means no anchor correction is needed. */
	@Test
	public void shiftYDefaultsToZero()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, null, null, null)), BOSS_NPC_IDS, false);

		assertEquals(0, spec.getShiftY());
	}

	/**
	 * docs/DECISIONS.md D26: the model's ground line sits at the widget's vertical center, so a
	 * curated shiftY moves the pool widget's rect to recenter a model whose own envelope isn't
	 * naturally centered on it.
	 */
	@Test
	public void explicitShiftYIsHonored()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, null, 268, null)), BOSS_NPC_IDS, false);

		assertEquals(268, spec.getShiftY());
	}

	/**
	 * docs/DECISIONS.md D27, {@code preview.modelId} override: absent by default, so
	 * {@code com.bossmechanics.ui} knows to resolve the model through the npc lookup instead.
	 */
	@Test
	public void modelIdDefaultsToNull()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, null, null, null)), BOSS_NPC_IDS, false);

		assertEquals(null, spec.getModelId());
	}

	/**
	 * docs/DECISIONS.md D27: an explicit {@code preview.modelId} is carried through untouched, so
	 * secondary models (and any mechanic whose distinct model needs no npc lookup) can bypass it.
	 */
	@Test
	public void explicitModelIdIsHonored()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, null, null, 17550)), BOSS_NPC_IDS, false);

		assertEquals(Integer.valueOf(17550), spec.getModelId());
	}

	private static Mechanic mechanic(Preview preview)
	{
		return new Mechanic("m1", "Name", "Description", "Counterplay", null,
			Collections.emptyList(), preview, null);
	}
}

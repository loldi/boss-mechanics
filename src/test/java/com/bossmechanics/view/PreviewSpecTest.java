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
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, null)), BOSS_NPC_IDS, true);

		assertFalse(spec.isVisible());
	}

	@Test
	public void npcIdDefaultsToTheBossFirstNpcId()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, null)), BOSS_NPC_IDS, false);

		assertEquals(111, spec.getNpcId());
	}

	@Test
	public void explicitNpcIdIsHonored()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, 222, false, null)), BOSS_NPC_IDS, false);

		assertEquals(222, spec.getNpcId());
	}

	@Test
	public void staticFallbackWinsOverAPresentAnimationId()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, true, null)), BOSS_NPC_IDS, false);

		assertEquals(PreviewSpec.NO_ANIMATION, spec.getAnimationId());
	}

	@Test
	public void missingAnimationIdFallsBackToAStaticPoseInsteadOfCrashing()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(null, null, false, null)), BOSS_NPC_IDS, false);

		assertEquals(PreviewSpec.NO_ANIMATION, spec.getAnimationId());
	}

	@Test
	public void zoomDefaultsTo3000()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, null)), BOSS_NPC_IDS, false);

		assertEquals(PreviewSpec.DEFAULT_ZOOM, spec.getZoom());
	}

	@Test
	public void explicitZoomIsHonored()
	{
		PreviewSpec spec = PreviewSpec.of(mechanic(new Preview(500, null, false, 1500)), BOSS_NPC_IDS, false);

		assertEquals(1500, spec.getZoom());
	}

	private static Mechanic mechanic(Preview preview)
	{
		return new Mechanic("m1", "Name", "Description", "Counterplay", null,
			Collections.emptyList(), preview, null);
	}
}

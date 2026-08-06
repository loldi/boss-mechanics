package com.bossmechanics.ui;

import static org.junit.Assert.assertEquals;

import com.bossmechanics.PerfRecorder;
import java.util.function.LongSupplier;
import net.runelite.api.events.ClientTick;
import org.junit.Test;

/**
 * Issue #81, slice 6: {@link BossMechanicsWindow#onClientTick} is the one live-gameplay entry
 * point that lives outside {@code BossMechanicsPlugin}, so it gets its own thin wrapper and its own
 * small test file rather than crowding {@link BossMechanicsWindowLayoutTest} (already 1000+ lines).
 *
 * <p>A bare {@code new BossMechanicsWindow()} is enough here: {@code onClientTick}'s real body
 * (mocked window (@code windowOpen}/{@code root} are both false/null on a fresh instance) short-
 * circuits before touching {@code client}, so nothing needs faking beyond the recorder itself.
 */
public class BossMechanicsWindowPerfTest
{
	@Test
	public void clientTickIsInstrumentedWhenRecorderEnabled()
	{
		CountingSupplier clock = new CountingSupplier();
		CountingSupplier alloc = new CountingSupplier();
		PerfRecorder recorder = new PerfRecorder(clock, alloc);
		recorder.setEnabled(true);

		BossMechanicsWindow window = new BossMechanicsWindow();
		window.setPerfRecorder(recorder);

		window.onClientTick(new ClientTick());

		assertEquals("one full tick must read the clock at start and end", 2, clock.calls());
		assertEquals("one full tick must read the allocation counter at start and end", 2,
			alloc.calls());
	}

	@Test
	public void clientTickSkipsSuppliersWhenRecorderUnsetOrDisabled()
	{
		BossMechanicsWindow noRecorder = new BossMechanicsWindow();
		noRecorder.onClientTick(new ClientTick());

		CountingSupplier clock = new CountingSupplier();
		CountingSupplier alloc = new CountingSupplier();
		PerfRecorder recorder = new PerfRecorder(clock, alloc);
		// deliberately left disabled

		BossMechanicsWindow window = new BossMechanicsWindow();
		window.setPerfRecorder(recorder);
		window.onClientTick(new ClientTick());

		assertEquals("a disabled recorder must never have its suppliers invoked", 0, clock.calls());
		assertEquals("a disabled recorder must never have its suppliers invoked", 0, alloc.calls());
	}

	/** A {@link LongSupplier} that counts its own invocations. */
	private static final class CountingSupplier implements LongSupplier
	{
		private int calls;

		@Override
		public long getAsLong()
		{
			calls++;
			return 0L;
		}

		int calls()
		{
			return calls;
		}
	}
}

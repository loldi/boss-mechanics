package com.bossmechanics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.Test;

public class PerfRecorderTest
{
	@Test
	public void disabledInvokesNoSuppliers()
	{
		CountingSupplier clock = new CountingSupplier(1_000L);
		CountingSupplier alloc = new CountingSupplier(64L);
		PerfRecorder recorder = new PerfRecorder(clock, alloc);

		runOneCycle(recorder);

		assertEquals("disabled: clock must never be read", 0, clock.calls());
		assertEquals("disabled: alloc must never be read", 0, alloc.calls());
		assertEquals(0, recorder.count(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED));

		recorder.setEnabled(true);
		runOneCycle(recorder);

		assertEquals("enabled: clock must be read once at start, once at end", 2, clock.calls());
		assertEquals("enabled: alloc must be read once at start, once at end", 2, alloc.calls());
		assertEquals(1, recorder.count(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED));
	}

	@Test
	public void latencyHistogramTracksPercentilesAndMaxExactly()
	{
		long[] deltas = new long[100];
		for (int i = 0; i < 99; i++)
		{
			deltas[i] = 1_000L;
		}
		deltas[99] = 1_000_000L;

		PerfRecorder recorder = new PerfRecorder(ScriptedSupplier.forDeltas(deltas), null);
		recorder.setEnabled(true);

		for (long ignored : deltas)
		{
			long a0 = recorder.allocStart();
			long t0 = recorder.timeStart();
			recorder.record(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED, t0, a0);
		}

		assertEquals("max must be exact, not bucket-quantized", 1_000_000L,
			recorder.maxNanos(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED));
		assertWithinRelativeError(1_000L,
			recorder.percentileNanos(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED, 50), 0.125);
		assertWithinRelativeError(1_000L,
			recorder.percentileNanos(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED, 99), 0.125);

		assertEquals("a handler nothing recorded to must stay untouched", 0,
			recorder.count(PerfRecorder.Handler.NPC_SPAWNED));

		recorder.setEnabled(true);
		assertEquals("re-enabling must reset counts so each scenario starts clean", 0,
			recorder.count(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED));
	}

	@Test
	public void allocationTotalsSumDeltasAndAppearInDump()
	{
		// alloc: 100 -> 164 (start), then 164 -> 264 (end) => +64 allocated during the call.
		ScriptedSupplier alloc = new ScriptedSupplier(new long[] {100L, 164L});
		ScriptedSupplier clock = new ScriptedSupplier(new long[] {0L, 1_000L});
		PerfRecorder recorder = new PerfRecorder(clock, alloc);
		recorder.setEnabled(true);

		long a0 = recorder.allocStart();
		long t0 = recorder.timeStart();
		recorder.record(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED, t0, a0);

		assertEquals(64L, recorder.totalBytes(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED));

		String dump = recorder.dump();
		assertTrue("dump must report the handler's total bytes", dump.contains("64"));
	}

	@Test
	public void nullAllocationSupplierNeverMisreportsZero()
	{
		PerfRecorder recorder = new PerfRecorder(new ScriptedSupplier(new long[] {0L, 1_000L}), null);
		recorder.setEnabled(true);

		long a0 = recorder.allocStart();
		long t0 = recorder.timeStart();
		recorder.record(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED, t0, a0);

		assertEquals("no supplier means nothing accumulates, but the count still recorded", 1,
			recorder.count(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED));

		String dump = recorder.dump();
		assertTrue("byte columns must read n/a, never a misleading 0, when allocation is unsupported",
			dump.contains("n/a"));
	}

	/**
	 * The ordering contract (docs/DECISIONS.md-worthy): {@code allocStart()} reads BEFORE
	 * {@code timeStart()} at the start of a call, and {@code record()} reads the clock BEFORE the
	 * allocation counter at the end -- so the (comparably expensive) JMX allocation read never sits
	 * inside the timed span. Verified here via one shared invocation log across both fake suppliers.
	 */
	@Test
	public void allocationReadsSitOutsideTheTimedSpan()
	{
		List<String> log = new ArrayList<>();
		LongSupplier clock = taggedSupplier(log, "clock");
		LongSupplier alloc = taggedSupplier(log, "alloc");
		PerfRecorder recorder = new PerfRecorder(clock, alloc);
		recorder.setEnabled(true);

		long a0 = recorder.allocStart();
		long t0 = recorder.timeStart();
		recorder.record(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED, t0, a0);

		assertEquals(Arrays.asList("alloc", "clock", "clock", "alloc"), log);
	}

	@Test
	public void dumpHasOneRowPerHandlerAndResetZeroesEverything()
	{
		PerfRecorder recorder = new PerfRecorder(new ScriptedSupplier(new long[] {0L, 1_000L}),
			new ScriptedSupplier(new long[] {0L, 64L}));
		recorder.setEnabled(true);

		long a0 = recorder.allocStart();
		long t0 = recorder.timeStart();
		recorder.record(PerfRecorder.Handler.NPC_SPAWNED, t0, a0);

		String dump = recorder.dump();
		for (PerfRecorder.Handler handler : PerfRecorder.Handler.values())
		{
			assertTrue("dump must have a row for every handler, missing " + handler,
				dump.contains(handler.name()));
		}

		recorder.reset();

		assertEquals(0, recorder.count(PerfRecorder.Handler.NPC_SPAWNED));
		assertEquals(0, recorder.maxNanos(PerfRecorder.Handler.NPC_SPAWNED));
		assertEquals(0, recorder.totalBytes(PerfRecorder.Handler.NPC_SPAWNED));
		assertEquals(0, recorder.percentileNanos(PerfRecorder.Handler.NPC_SPAWNED, 50));
	}

	/**
	 * LOAD-BEARING (issue #81's open question). Proves two things about the real, JVM-backed
	 * supplier that every other test above fakes: (a) calling it back-to-back with nothing between,
	 * once warmed up, reports EXACTLY zero bytes -- the JMX call itself does not self-allocate, which
	 * is what makes the bytes-per-call methodology this whole feature rests on valid; (b) it actually
	 * measures a real allocation. If (a) ever fails on CI, that is a finding to report, not a bug to
	 * paper over: it would mean per-handler bytes/call numbers include the JMX read's own cost and
	 * cannot be trusted at face value.
	 */
	@Test
	public void defaultAllocationSupplierWorks()
	{
		LongSupplier supplier = PerfRecorder.defaultAllocationSupplier();
		assumeNotNull(supplier);

		supplier.getAsLong(); // warm up: the very first call can itself allocate (class init, etc).

		long before = supplier.getAsLong();
		long after = supplier.getAsLong();
		assertEquals("the JMX call itself must not self-allocate", 0L, after - before);

		long a0 = supplier.getAsLong();
		byte[] block = new byte[1024 * 1024];
		long a1 = supplier.getAsLong();
		assertNotNull(block);
		assertTrue("a real 1MB allocation must be visible", (a1 - a0) >= 1024 * 1024);
	}

	private static LongSupplier taggedSupplier(List<String> log, String tag)
	{
		return () -> {
			log.add(tag);
			return 0L;
		};
	}

	private static void assertWithinRelativeError(long expected, long actual, double maxRelativeError)
	{
		double relativeError = Math.abs(actual - expected) / (double) expected;
		assertTrue(String.format("expected ~%d within %.1f%% relative error, got %d (%.1f%% off)",
				expected, maxRelativeError * 100, actual, relativeError * 100),
			relativeError <= maxRelativeError);
	}

	/** Mirrors the wrapper idiom every real handler uses: gate on {@code isEnabled()} alone. */
	private static void runOneCycle(PerfRecorder recorder)
	{
		if (!recorder.isEnabled())
		{
			return;
		}
		long a0 = recorder.allocStart();
		long t0 = recorder.timeStart();
		recorder.record(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED, t0, a0);
	}

	/** A {@link LongSupplier} that counts its own invocations, returning a constant fallback value. */
	static final class CountingSupplier implements LongSupplier
	{
		private final long fallback;
		private int index;

		CountingSupplier(long fallback)
		{
			this.fallback = fallback;
		}

		@Override
		public long getAsLong()
		{
			index++;
			return fallback;
		}

		int calls()
		{
			return index;
		}
	}

	/**
	 * A {@link LongSupplier} that replays a fixed sequence of values in order, so a test can script
	 * exact deltas between a {@code timeStart()}/{@code record()} pair.
	 */
	static final class ScriptedSupplier implements LongSupplier
	{
		private final long[] values;
		private int index;

		ScriptedSupplier(long[] values)
		{
			this.values = values;
		}

		@Override
		public long getAsLong()
		{
			return values[index++];
		}

		/**
		 * Builds a supplier that answers {@code 0, deltas[0], 0, deltas[1], ...} -- each pair is one
		 * {@code timeStart()}/{@code record()} cycle, so {@code record()} computes exactly
		 * {@code deltas[i]} as that cycle's elapsed time.
		 */
		static ScriptedSupplier forDeltas(long[] deltas)
		{
			long[] values = new long[deltas.length * 2];
			for (int i = 0; i < deltas.length; i++)
			{
				values[2 * i] = 0L;
				values[(2 * i) + 1] = deltas[i];
			}
			return new ScriptedSupplier(values);
		}
	}
}

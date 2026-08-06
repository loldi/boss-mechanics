package com.bossmechanics;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.function.LongSupplier;

/**
 * Debug-flag-gated client-thread cost accounting (issue #81). RuneLite-free by construction (only
 * {@code java.*}, plus {@code com.sun.management} isolated to {@link #defaultAllocationSupplier()}
 * alone) so it is plain-JUnit testable and the caller supplies both clocks.
 *
 * <p>Per {@link Handler}: call count, an HdrHistogram-style wall-time histogram (p50/p99 read off
 * it, max tracked exactly), and total bytes allocated. Every accumulating array is preallocated at
 * construction and indexed by {@code Handler.ordinal()}/bucket index only -- {@link #record} never
 * allocates, boxes or builds a string.
 *
 * <p><b>Zero cost when disabled is the caller's job, not this class's.</b> {@link #allocStart()},
 * {@link #timeStart()} and {@link #record} unconditionally invoke their suppliers; every real call
 * site gates on {@link #isEnabled()} first (a single volatile-backed boolean read) and skips this
 * class entirely when it is false, exactly mirroring the pattern this test class exercises directly
 * via {@code isEnabled()} rather than baking a redundant check into the hot methods themselves.
 */
public final class PerfRecorder
{
	public enum Handler
	{
		NPC_SPAWNED, NPC_CHANGED, NPC_DESPAWNED, ANIMATION_CHANGED,
		PROJECTILE_MOVED, GRAPHICS_OBJECT_CREATED, WINDOW_CLIENT_TICK
	}

	private static final int HANDLER_COUNT = Handler.values().length;

	/**
	 * HdrHistogram-style log-linear bucketing: values below {@link #SUB_BUCKETS} get one bucket
	 * each (exact), every octave above that is split into {@link #SUB_BUCKETS} equal-width linear
	 * sub-buckets. Worst-case relative error is fixed at {@code 1 / SUB_BUCKETS} (12.5%), occurring
	 * at the low edge of a sub-bucket; later sub-buckets and larger values within the same octave
	 * are more accurate. {@link #BUCKET_COUNT} covers every octave a positive {@code long} can hold.
	 */
	private static final int SUB_BUCKET_BITS = 3;
	private static final int SUB_BUCKETS = 1 << SUB_BUCKET_BITS;
	private static final int MAX_MSB = 63;
	private static final int BUCKET_COUNT = SUB_BUCKETS * (MAX_MSB - SUB_BUCKET_BITS + 2);

	private final LongSupplier nanoClock;

	/** Nullable: {@code null} means allocation accounting is unsupported on this JVM. */
	private final LongSupplier allocatedBytes;

	private final long[] counts = new long[HANDLER_COUNT];
	private final long[] maxNanos = new long[HANDLER_COUNT];
	private final long[] totalBytes = new long[HANDLER_COUNT];
	private final long[][] buckets = new long[HANDLER_COUNT][BUCKET_COUNT];

	private volatile boolean enabled;

	public PerfRecorder(LongSupplier nanoClock, LongSupplier allocatedBytes)
	{
		this.nanoClock = nanoClock;
		this.allocatedBytes = allocatedBytes;
	}

	/**
	 * Builds the real, JVM-backed allocation supplier, or {@code null} if this JVM cannot answer
	 * (no {@code com.sun.management.ThreadMXBean}, or it does not support per-thread allocation
	 * accounting). The only place this class touches {@code com.sun.management} -- isolated here so
	 * a later decision to drop the import entirely stays a one-method change. Any throw during setup
	 * is treated as "unsupported": callers should never crash over a debug tool.
	 *
	 * <p>Reads {@code Thread.currentThread().getId()} inside the returned supplier, not once here at
	 * construction time, so the supplier answers correctly no matter which thread ends up calling
	 * it -- the JDK 11 {@code getThreadAllocatedBytes(long)} API takes a thread id explicitly;
	 * {@code getCurrentThreadAllocatedBytes()} is JDK 14+ and unavailable on this project's
	 * source/target (11).
	 */
	public static LongSupplier defaultAllocationSupplier()
	{
		try
		{
			ThreadMXBean bean = ManagementFactory.getThreadMXBean();
			if (!(bean instanceof com.sun.management.ThreadMXBean))
			{
				return null;
			}

			com.sun.management.ThreadMXBean sunBean = (com.sun.management.ThreadMXBean) bean;
			if (!sunBean.isThreadAllocatedMemorySupported())
			{
				return null;
			}
			if (!sunBean.isThreadAllocatedMemoryEnabled())
			{
				sunBean.setThreadAllocatedMemoryEnabled(true);
			}

			return () -> sunBean.getThreadAllocatedBytes(Thread.currentThread().getId());
		}
		catch (Throwable t)
		{
			return null;
		}
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	/** Enabling always resets: every run starts clean rather than accumulating across toggles. */
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
		if (enabled)
		{
			reset();
		}
	}

	public void reset()
	{
		Arrays.fill(counts, 0L);
		Arrays.fill(maxNanos, 0L);
		Arrays.fill(totalBytes, 0L);
		for (long[] handlerBuckets : buckets)
		{
			Arrays.fill(handlerBuckets, 0L);
		}
	}

	/**
	 * Read the allocation counter. Ordering contract with {@link #timeStart()}/{@link #record}: this
	 * must be called BEFORE {@link #timeStart()} at the start of a measured call, so the (comparably
	 * expensive) JMX read sits outside the timed span, not inside it.
	 */
	public long allocStart()
	{
		return allocatedBytes == null ? 0L : allocatedBytes.getAsLong();
	}

	public long timeStart()
	{
		return nanoClock.getAsLong();
	}

	/**
	 * Ends one measured call. Reads the clock FIRST (closing the timed span as early as possible),
	 * then the allocation counter (deliberately outside the timed span, mirroring
	 * {@link #allocStart()}'s ordering at the other end).
	 */
	public void record(Handler handler, long t0, long a0)
	{
		long t1 = nanoClock.getAsLong();
		long a1 = allocatedBytes == null ? 0L : allocatedBytes.getAsLong();

		int h = handler.ordinal();
		long deltaNanos = t1 - t0;

		counts[h]++;
		if (deltaNanos > maxNanos[h])
		{
			maxNanos[h] = deltaNanos;
		}
		buckets[h][bucketIndex(deltaNanos)]++;

		if (allocatedBytes != null)
		{
			totalBytes[h] += (a1 - a0);
		}
	}

	/** @return the log table: one row per {@link Handler}, plus a header noting bucket resolution. */
	public String dump()
	{
		boolean allocSupported = allocatedBytes != null;
		StringBuilder sb = new StringBuilder();
		sb.append("Boss Mechanics perf dump (percentiles are bucket-resolution, <=12.5% worst-case ")
			.append("error; times in microseconds)\n");
		sb.append(String.format("%-22s %8s %10s %10s %10s %14s %14s%n",
			"handler", "count", "p50us", "p99us", "maxus", "totalBytes", "bytesPerCall"));

		for (Handler handler : Handler.values())
		{
			int h = handler.ordinal();
			long count = counts[h];
			long p50 = percentileNanos(handler, 50) / 1000;
			long p99 = percentileNanos(handler, 99) / 1000;
			long max = maxNanos[h] / 1000;
			String bytesCol = allocSupported ? String.valueOf(totalBytes[h]) : "n/a";
			String perCallCol = !allocSupported ? "n/a"
				: (count == 0 ? "0" : String.valueOf(totalBytes[h] / count));

			sb.append(String.format("%-22s %8d %10d %10d %10d %14s %14s%n",
				handler, count, p50, p99, max, bytesCol, perCallCol));
		}

		return sb.toString();
	}

	long count(Handler handler)
	{
		return counts[handler.ordinal()];
	}

	long maxNanos(Handler handler)
	{
		return maxNanos[handler.ordinal()];
	}

	long totalBytes(Handler handler)
	{
		return totalBytes[handler.ordinal()];
	}

	/**
	 * @param percentile in (0, 100]
	 * @return the upper bound of the bucket containing the requested percentile, or 0 if nothing has
	 * been recorded yet
	 */
	long percentileNanos(Handler handler, double percentile)
	{
		int h = handler.ordinal();
		long total = counts[h];
		if (total == 0)
		{
			return 0L;
		}

		long target = (long) Math.ceil(total * percentile / 100.0);
		if (target < 1)
		{
			target = 1;
		}

		long cumulative = 0;
		long[] handlerBuckets = buckets[h];
		for (int i = 0; i < BUCKET_COUNT; i++)
		{
			cumulative += handlerBuckets[i];
			if (cumulative >= target)
			{
				return bucketUpperBound(i);
			}
		}
		return maxNanos[h];
	}

	private static int bucketIndex(long nanos)
	{
		if (nanos < SUB_BUCKETS)
		{
			return (int) nanos;
		}

		int msb = 63 - Long.numberOfLeadingZeros(nanos);
		long octaveBase = 1L << msb;
		long subBucketWidth = octaveBase >>> SUB_BUCKET_BITS;
		int subIndex = (int) ((nanos - octaveBase) / subBucketWidth);
		return SUB_BUCKETS + ((msb - SUB_BUCKET_BITS) * SUB_BUCKETS) + subIndex;
	}

	private static long bucketUpperBound(int bucketIndex)
	{
		if (bucketIndex < SUB_BUCKETS)
		{
			return bucketIndex;
		}

		int offset = bucketIndex - SUB_BUCKETS;
		int msb = SUB_BUCKET_BITS + (offset / SUB_BUCKETS);
		int subIndex = offset % SUB_BUCKETS;
		long octaveBase = 1L << msb;
		long subBucketWidth = octaveBase >>> SUB_BUCKET_BITS;
		return octaveBase + ((subIndex + 1) * subBucketWidth);
	}
}

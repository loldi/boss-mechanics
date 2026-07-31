package com.bossmechanics.data;

import java.util.List;
import lombok.Value;

/** Outcome of a load: every boss that parsed and validated cleanly, plus every error found. */
@Value
public class LoadResult
{
	List<Boss> bosses;
	List<String> errors;
}

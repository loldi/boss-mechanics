package com.bossmechanics.data;

import java.util.List;
import lombok.Value;

/**
 * One boss move (see data/SCHEMA.md "Mechanic"). {@code phase}, {@code requires} and
 * {@code wikiUrl} are nullable.
 */
@Value
public class Mechanic
{
	String id;
	String name;
	String description;
	String counterplay;
	String phase;
	List<Trigger> detection;
	/** Game-state gate on discoverability (docs/DECISIONS.md D38). Null means always discoverable. */
	Requirement requires;
	Preview preview;
	String wikiUrl;
}

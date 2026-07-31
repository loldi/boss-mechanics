package com.bossmechanics.data;

import java.util.List;
import lombok.Value;

/** One boss move (see data/SCHEMA.md "Mechanic"). {@code phase} and {@code wikiUrl} are nullable. */
@Value
public class Mechanic
{
	String id;
	String name;
	String description;
	String counterplay;
	String phase;
	List<Trigger> detection;
	Preview preview;
	String wikiUrl;
}

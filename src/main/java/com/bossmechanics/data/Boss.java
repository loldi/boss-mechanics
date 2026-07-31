package com.bossmechanics.data;

import java.util.List;
import lombok.Value;

/** One bundled boss data file (see data/SCHEMA.md), deserialized as-is by Gson. */
@Value
public class Boss
{
	String id;
	String name;
	List<Integer> npcIds;
	String wikiUrl;
	List<Mechanic> mechanics;
}

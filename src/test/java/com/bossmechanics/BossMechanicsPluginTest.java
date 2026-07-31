package com.bossmechanics;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BossMechanicsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BossMechanicsPlugin.class);
		RuneLite.main(args);
	}
}

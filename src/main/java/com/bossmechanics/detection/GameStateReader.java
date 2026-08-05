package com.bossmechanics.detection;

/**
 * Reads live game state a mechanic's {@code requires} gate needs (docs/DECISIONS.md D38). The
 * seam that keeps {@code com.bossmechanics.detection} free of every {@code net.runelite} import:
 * {@code BossMechanicsPlugin} supplies {@code client::getVarpValue} as the real implementation;
 * tests supply a fixed-value or recording lambda.
 */
@FunctionalInterface
public interface GameStateReader
{
	int varp(int id);
}

package org.ultramine.mods.anticheat;

import java.util.List;
import java.util.Map;

public class UMACConfig
{
	public AntiXRay antiXRay;

	public static class AntiXRay
	{
		public boolean enabled;
		public String strategy;
		public List<String> oreBlocks;
		public Map<Integer, String> worldProviderToStoneBlock;
	}
}

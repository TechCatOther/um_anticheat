package org.ultramine.mods.anticheat;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.command.CommandException;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.oredict.OreDictionary;
import net.openhft.koloboke.collect.map.IntIntMap;
import net.openhft.koloboke.collect.map.hash.HashIntIntMaps;
import net.openhft.koloboke.collect.set.ShortSet;
import net.openhft.koloboke.collect.set.hash.HashShortSets;
import org.ultramine.core.service.EventBusRegisteredService;
import org.ultramine.server.chunk.AntiXRayService;
import org.ultramine.server.chunk.ChunkSnapshot;
import org.ultramine.server.event.SetBlockEvent;
import org.ultramine.server.util.BasicTypeParser;

import java.util.List;
import java.util.Map;

public class AntiXRayServiceImpl extends EventBusRegisteredService implements AntiXRayService<Integer>
{
	private final CanReplaceStrategy strategy;
	private final IntIntMap worldProviderToStoneType;

	public AntiXRayServiceImpl(UMACConfig.AntiXRay config)
	{
		if(config.strategy.equals("replace-ore-to-stone"))
			this.strategy = new ReplaceIfContainsStrategy(createBlockSet(config.oreBlocks));
		else if(config.strategy.equals("replace-all-to-stone"))
			this.strategy = new ReplaceAllStrategy();
		else
			throw new IllegalArgumentException("Unknown antiXRay strategy in settings/anticheat.yml: " + config.strategy);
		this.worldProviderToStoneType = createWorldToBlockMap(config.worldProviderToStoneBlock);
	}

	private ShortSet createBlockSet(List<String> oreBlocks)
	{
		ShortSet tempSet = HashShortSets.newUpdatableSet();
		for(String blockStr : oreBlocks)
		{
			ItemStack is;
			try
			{
				is = BasicTypeParser.parseItemStack(blockStr, true, 1, false);
			} catch (CommandException e) {
				continue;
			}
			int id = Item.itemRegistry.getIDForObject(is.getItem());
			if(is.getItemDamage() == OreDictionary.WILDCARD_VALUE)
			{
				for(int i = 0; i < 16; i++)
					addIdMeta(tempSet, id, i);
			}
			else
			{
				addIdMeta(tempSet, id, is.getItemDamage());
			}
		}
		return HashShortSets.newImmutableSet(tempSet);
	}

	private void addIdMeta(ShortSet set, int id, int meta)
	{
		set.add((short)(id | ((meta & 15) << 12)));
	}

	private IntIntMap createWorldToBlockMap(Map<Integer, String> worldProviderToStoneBlock)
	{
		IntIntMap tempMap = HashIntIntMaps.newUpdatableMap();

		for(Map.Entry<Integer, String> ent : worldProviderToStoneBlock.entrySet())
		{
			ItemStack is;
			try
			{
				is = BasicTypeParser.parseItemStack(ent.getValue(), false, 1, false);
			} catch (CommandException e) {
				continue;
			}
			int id = Item.itemRegistry.getIDForObject(is.getItem());
			tempMap.put(ent.getKey().intValue(), (id | ((is.getItemDamage() & 15) << 12)));
		}

		return HashIntIntMaps.newImmutableMap(tempMap);
	}

	private boolean canReplaceType(int idMeta)
	{
		return strategy.canReplaceType(idMeta);
	}

	private boolean canReplaceType(int id, int meta)
	{
		return canReplaceType(id | ((meta & 15) << 12));
	}

	private boolean canReplaceType(Block block, int meta)
	{
		return canReplaceType(Block.blockRegistry.getIDForObject(block), meta);
	}

	private boolean canReplaceBlock(ChunkSnapshot chunkSnapshot, int x, int y, int z)
	{
		int idMeta = chunkSnapshot.getBlockIdAndMeta(x, y, z);

		return
				canReplaceType(idMeta) &&
				chunkSnapshot.getBlock(x-1,	y,		z)	.isOpaqueCube() &&
				chunkSnapshot.getBlock(x+1,	y,		z)	.isOpaqueCube() &&
				chunkSnapshot.getBlock(x,	y,		z-1).isOpaqueCube() &&
				chunkSnapshot.getBlock(x,	y,		z+1).isOpaqueCube() &&
				chunkSnapshot.getBlock(x,	y-1,	z)	.isOpaqueCube() &&
				chunkSnapshot.getBlock(x,	y+1,	z)	.isOpaqueCube();
	}

	private boolean canReplaceBlock(World world, int x, int y, int z)
	{
		Block block = world.getBlock(x, y, z);
		int meta = world.getBlockMetadata(x, y, z);

		return
				canReplaceType(block, meta) &&
				world.getBlock(x-1,	y,		z)	.isOpaqueCube() &&
				world.getBlock(x+1,	y,		z)	.isOpaqueCube() &&
				world.getBlock(x,	y,		z-1).isOpaqueCube() &&
				world.getBlock(x,	y,		z+1).isOpaqueCube() &&
				world.getBlock(x,	y-1,	z)	.isOpaqueCube() &&
				world.getBlock(x,	y+1,	z)	.isOpaqueCube();
	}

	private boolean canReplaceBlock(World world, ChunkSnapshot chunkSnapshot, int x, int y, int z)
	{
		return canReplaceBlock(world, (chunkSnapshot.getX() << 4) | x, y, (chunkSnapshot.getZ() << 4) | z);
	}

	@Override
	public Integer prepareChunkSync(ChunkSnapshot chunkSnapshot, Chunk chunk)
	{
		World world = chunk.worldObj;
		int stone = worldProviderToStoneType.getOrDefault(DimensionManager.getProviderType(world.provider.dimensionId), 1);
		int stoneId = stone & 0xFFF;
		int stoneMeta = stone >> 12;
		int yMax = Math.min(chunkSnapshot.getTopFilledSegment() + 16, 254);
		for(int y = 1; y < yMax; y++)
			for(int x = 0; x < 16; x++)
				if(canReplaceBlock(world, chunkSnapshot, x, y, 0))
					chunkSnapshot.setBlock(x, y, 0, stoneId, stoneMeta);
		for(int y = 1; y < yMax; y++)
			for(int x = 0; x < 16; x++)
				if(canReplaceBlock(world, chunkSnapshot, x, y, 15))
					chunkSnapshot.setBlock(x, y, 15, stoneId, stoneMeta);
		for(int y = 1; y < yMax; y++)
			for(int z = 1; z < 15; z++)
				if(canReplaceBlock(world, chunkSnapshot, 0, y, z))
					chunkSnapshot.setBlock(0, y, z, stoneId, stoneMeta);
		for(int y = 1; y < yMax; y++)
			for(int z = 1; z < 15; z++)
				if(canReplaceBlock(world, chunkSnapshot, 15, y, z))
					chunkSnapshot.setBlock(15, y, z, stoneId, stoneMeta);
		return stone;
	}

	@Override
	public void prepareChunkAsync(ChunkSnapshot chunkSnapshot, Integer stoneType)
	{
		int stone = stoneType;
		int stoneId = stone & 0xFFF;
		int stoneMeta = stone >> 12;
		int yMax = Math.min(chunkSnapshot.getTopFilledSegment() + 16, 254);
		for(int y = 1; y < yMax; y++)
			for(int z = 1; z < 15; z++)
				for(int x = 1; x < 15; x++)
					if(canReplaceBlock(chunkSnapshot, x, y, z))
						chunkSnapshot.setBlock(x, y, z, stoneId, stoneMeta);
	}

	private void checkAndUpdateLocation(World world, int x, int y, int z)
	{
		if(canReplaceBlock(world, x, y, z))
			world.markBlockForUpdate(x, y, z);
	}

	@SubscribeEvent
	public void onBlockChange(SetBlockEvent e)
	{
		if(e.newBlock.isOpaqueCube() || !e.world.getBlock(e.x, e.y, e.z).isOpaqueCube())
			return;
		checkAndUpdateLocation(e.world, e.x-1, e.y, e.z);
		checkAndUpdateLocation(e.world, e.x+1, e.y, e.z);
		checkAndUpdateLocation(e.world, e.x, e.y, e.z-1);
		checkAndUpdateLocation(e.world, e.x, e.y, e.z+1);
		checkAndUpdateLocation(e.world, e.x, e.y-1, e.z);
		checkAndUpdateLocation(e.world, e.x, e.y+1, e.z);
	}

	interface CanReplaceStrategy
	{
		boolean canReplaceType(int idMeta);
	}

	private static class ReplaceAllStrategy implements CanReplaceStrategy
	{
		@Override
		public boolean canReplaceType(int idMeta)
		{
			return true;
		}
	}

	private static class ReplaceIfContainsStrategy implements CanReplaceStrategy
	{
		private final ShortSet oreBlocks;

		private ReplaceIfContainsStrategy(ShortSet oreBlocks)
		{
			this.oreBlocks = oreBlocks;
		}

		@Override
		public boolean canReplaceType(int idMeta)
		{
			return oreBlocks.contains((short)idMeta);
		}
	}
}

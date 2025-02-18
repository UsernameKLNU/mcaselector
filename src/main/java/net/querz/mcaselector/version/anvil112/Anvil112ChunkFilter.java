package net.querz.mcaselector.version.anvil112;

import net.querz.mcaselector.debug.Debug;
import net.querz.mcaselector.io.registry.BiomeRegistry;
import net.querz.mcaselector.tiles.Tile;
import net.querz.mcaselector.version.ChunkFilter;
import net.querz.mcaselector.version.Helper;
import net.querz.nbt.tag.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Anvil112ChunkFilter implements ChunkFilter {

	private final Map<String, BlockData[]> mapping = new HashMap<>();

	public Anvil112ChunkFilter() {
		try (BufferedReader bis = new BufferedReader(
				new InputStreamReader(Objects.requireNonNull(Anvil112ChunkFilter.class.getClassLoader().getResourceAsStream("mapping/block_name_to_id.txt"))))) {
			String line;
			while ((line = bis.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("#")) {
					continue;
				}
				String[] elements = line.split(";");
				if (elements.length != 3 && !line.endsWith(";")) {
					Debug.error("invalid line in block id mapping file: \"" + line + "\"");
					continue;
				}

				int id;
				Set<Byte> data = new HashSet<>();
				try {
					id = Integer.parseInt(elements[1]);
					String[] stringBytes;
					if (elements.length == 2 || (stringBytes = elements[2].split(",")).length == 0) {
						for (int i = 0; i < 16; i++) {
							data.add((byte) i);
						}
					} else {
						for (String stringByte : stringBytes) {
							data.add(Byte.parseByte(stringByte));
						}
					}
				} catch (NumberFormatException ex) {
					Debug.dumpException(String.format("unable to parse block id or data in block id mapping file: \"%s\"", line), ex);
					continue;
				}

				String[] names = elements[0].split(",");
				for (String name : names) {
					String fullName = "minecraft:" + name;
					BlockData blockData = new BlockData(id, data);
					BlockData[] array = mapping.get(fullName);
					if (array != null) {
						BlockData[] newArray = new BlockData[array.length + 1];
						System.arraycopy(array, 0, newArray, 0, array.length);
						newArray[newArray.length - 1] = blockData;
						array = newArray;
					} else {
						array = new BlockData[1];
						array[0] = blockData;
					}
					mapping.put(fullName, array);
				}
			}

		} catch (IOException ex) {
			throw new RuntimeException("failed to open mapping/block_name_to_id.txt");
		}
	}

	@Override
	public boolean matchBlockNames(CompoundTag data, Collection<String> names) {
		ListTag<CompoundTag> sections = Helper.tagFromLevelFromRoot(data, "Sections", null);
		if (sections == null) {
			return false;
		}
		int c = 0;
		nameLoop:
		for (String name : names) {
			BlockData[] bd = mapping.get(name);
			if (bd == null) {
				Debug.dump("no mapping found for " + name);
				continue;
			}
			for (CompoundTag t : sections) {
				byte[] blocks = Helper.byteArrayFromCompound(t, "Blocks");
				if (blocks == null) {
					continue;
				}
				byte[] blockData = Helper.byteArrayFromCompound(t, "Data");
				if (blockData == null) {
					continue;
				}

				for (int i = 0; i < blocks.length; i++) {
					short b = (short) (blocks[i] & 0xFF);
					for (BlockData d : bd) {
						if (d.id == b) {
							byte dataByte = (byte) (i % 2 == 0 ? blockData[i / 2] & 0x0F : (blockData[i / 2] >> 4) & 0x0F);
							if (d.data.contains(dataByte)) {
								c++;
								continue nameLoop;
							}
						}
					}
				}
			}
		}
		return names.size() == c;
	}

	@Override
	public boolean matchAnyBlockName(CompoundTag data, Collection<String> names) {
		ListTag<CompoundTag> sections = Helper.tagFromLevelFromRoot(data, "Sections", null);
		if (sections == null) {
			return false;
		}
		for (String name : names) {
			BlockData[] bd = mapping.get(name);
			if (bd == null) {
				Debug.dump("no mapping found for " + name);
				continue;
			}
			for (CompoundTag t : sections) {
				byte[] blocks = Helper.byteArrayFromCompound(t, "Blocks");
				if (blocks == null) {
					continue;
				}
				byte[] blockData = Helper.byteArrayFromCompound(t, "Data");
				if (blockData == null) {
					continue;
				}

				for (int i = 0; i < blocks.length; i++) {
					short b = (short) (blocks[i] & 0xFF);
					for (BlockData d : bd) {
						if (d.id == b) {
							byte dataByte = (byte) (i % 2 == 0 ? blockData[i / 2] & 0x0F : (blockData[i / 2] >> 4) & 0x0F);
							if (d.data.contains(dataByte)) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	@Override
	public boolean paletteEquals(CompoundTag data, Collection<String> names) {
		ListTag<CompoundTag> sections = Helper.tagFromLevelFromRoot(data, "Sections", null);
		if (sections == null) {
			return false;
		}

		Set<Block> blocks = new HashSet<>();
		List<BlockData> blockData = new ArrayList<>(names.size());
		for (String name : names) {
			BlockData[] bd = mapping.get(name);
			if (bd == null) {
				Debug.dump("no mapping found for " + name);
				continue;
			}
			blockData.addAll(Arrays.asList(bd));
		}

		for (CompoundTag t : sections) {
			byte[] blockBytes = Helper.byteArrayFromCompound(t, "Blocks");
			if (blockBytes == null) {
				continue;
			}
			byte[] dataBits = Helper.byteArrayFromCompound(t, "Data");
			if (dataBits == null) {
				continue;
			}

			blockLoop:
			for (int i = 0; i < blockBytes.length; i++) {
				short b = (short) (blockBytes[i] & 0xFF);
				for (BlockData d : blockData) {
					if (b == d.id) {
						byte dataByte = (byte) (i % 2 == 0 ? dataBits[i / 2] & 0x0F : (dataBits[i / 2] >> 4) & 0x0F);
						if (d.data.contains(dataByte)) {
							blocks.add(new Block(b, dataByte));
							continue blockLoop;
						}
					}
				}
				// there's a block in this chunk that we are not searching for, so we return right now
				return false;
			}
		}

		blockDataLoop:
		for (BlockData bd : blockData) {
			for (Block block : blocks) {
				if (bd.id == block.id && bd.data.contains(block.data)) {
					continue blockDataLoop;
				}
			}
			// blockData contains a block that does not exist in blocks (a block does not exist in this chunk)
			return false;
		}

		return true;
	}

	private static class BlockData {
		int id;
		Set<Byte> data;

		BlockData(int id, Set<Byte> data) {
			this.id = id;
			this.data = data;
		}

		@Override
		public String toString() {
			return "{" + id + ":" + Arrays.toString(data.toArray()) + "}";
		}
	}

	private static class Block {
		int id;
		byte data;

		Block(int id, byte data) {
			this.id = id;
			this.data = data;
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, data);
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Block && ((Block) other).id == id && ((Block) other).data == data;
		}
	}

	@Override
	public boolean matchBiomes(CompoundTag data, Collection<BiomeRegistry.BiomeIdentifier> biomes) {
		ByteArrayTag biomesTag = Helper.tagFromLevelFromRoot(data, "Biomes", null);
		if (biomesTag == null) {
			return false;
		}

		filterLoop:
		for (BiomeRegistry.BiomeIdentifier identifier : biomes) {
			for (byte dataID : biomesTag.getValue()) {
				if (identifier.matches(dataID)) {
					continue filterLoop;
				}
			}
			return false;
		}
		return true;
	}

	@Override
	public boolean matchAnyBiome(CompoundTag data, Collection<BiomeRegistry.BiomeIdentifier> biomes) {
		ByteArrayTag biomesTag = Helper.tagFromLevelFromRoot(data, "Biomes", null);
		if (biomesTag == null) {
			return false;
		}

		for (BiomeRegistry.BiomeIdentifier identifier : biomes) {
			for (byte dataID : biomesTag.getValue()) {
				if (identifier.matches(dataID)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void changeBiome(CompoundTag data, BiomeRegistry.BiomeIdentifier biome) {
		ByteArrayTag biomesTag = Helper.tagFromLevelFromRoot(data, "Biomes", null);
		if (biomesTag != null) {
			Arrays.fill(biomesTag.getValue(), (byte) biome.getID());
		}
	}

	@Override
	public void forceBiome(CompoundTag data, BiomeRegistry.BiomeIdentifier biome) {
		CompoundTag level = Helper.levelFromRoot(data);
		if (level != null) {
			byte[] biomes = new byte[256];
			Arrays.fill(biomes, (byte) biome.getID());
			level.putByteArray("Biomes", biomes);
		}
	}

	@Override
	public void replaceBlocks(CompoundTag data, Map<String, BlockReplaceData> replace) {
		ListTag<CompoundTag> sections = Helper.tagFromLevelFromRoot(data, "Sections", null);
		if (sections == null) {
			return;
		}

		// handle the special case when someone wants to replace air with something else
		if (replace.containsKey("minecraft:air")) {
			Map<Integer, CompoundTag> sectionMap = new HashMap<>();
			List<Integer> heights = new ArrayList<>(18);
			for (CompoundTag section : sections) {
				sectionMap.put(section.getNumber("Y").intValue(), section);
				heights.add(section.getNumber("Y").intValue());
			}

			for (int y = 0; y < 16; y++) {
				if (!sectionMap.containsKey(y)) {
					sectionMap.put(y, createEmptySection(y));
					heights.add(y);
				} else {
					CompoundTag section = sectionMap.get(y);
					if (!section.containsKey("Blocks") || !section.containsKey("Data")) {
						sectionMap.put(y, createEmptySection(y));
					}
				}
			}

			heights.sort(Integer::compareTo);
			sections.clear();

			for (int height : heights) {
				sections.add(sectionMap.get(height));
			}
		}

		for (CompoundTag section : sections) {
			for (Map.Entry<String, BlockReplaceData> entry : replace.entrySet()) {
				BlockData[] bd = mapping.get(entry.getKey());
				BlockData bdr = mapping.get(entry.getValue().getName())[0];

				byte[] blocks = section.getByteArray("Blocks");
				byte[] blockData = section.getByteArray("Data");

				section.remove("BlockLight");
				section.remove("SkyLight");

				blockLoop:
				for (int i = 0; i < blocks.length; i++) {
					byte dataByte = blockData[i / 2];
					byte dataBits = (byte) (i % 2 == 0 ? dataByte & 0x0F : (dataByte >> 4) & 0x0F);
					for (BlockData d : bd) {
						if (d.id == (blocks[i] & 0xFF) && d.data.contains(dataBits)) {
							blocks[i] = (byte) bdr.id;
							byte newDataBits = bdr.data.iterator().next();
							blockData[i / 2] = (byte) (i % 2 == 0 ? (dataByte & 0xF0) + newDataBits : (dataByte & 0x0F) + (newDataBits << 4));
							continue blockLoop;
						}
					}
				}
			}
		}

		// delete tile entities with that name

		ListTag<CompoundTag> tileEntities = Helper.tagFromLevelFromRoot(data, "TileEntities", null);
		if (tileEntities != null) {
			for (int i = 0; i < tileEntities.size(); i++) {
				CompoundTag tileEntity = tileEntities.get(i);
				String id = Helper.stringFromCompound(tileEntity, "id");
				if (id != null && replace.containsKey(id)) {
					tileEntities.remove(i);
					i--;
				}
			}
		}
	}

	protected CompoundTag createEmptySection(int y) {
		CompoundTag newSection = new CompoundTag();
		newSection.putByte("Y", (byte) y);
		newSection.putByteArray("Blocks", new byte[4096]);
		newSection.putByteArray("Data", new byte[2048]);
		return newSection;
	}

	@Override
	public int getAverageHeight(CompoundTag data) {
		ListTag<CompoundTag> sections = Helper.tagFromLevelFromRoot(data, "Sections", null);
		if (sections == null) {
			return 0;
		}

		sections.sort(this::filterSections);

		int totalHeight = 0;

		for (int cx = 0; cx < Tile.CHUNK_SIZE; cx++) {
			zLoop:
			for (int cz = 0; cz < Tile.CHUNK_SIZE; cz++) {
				for (CompoundTag section : sections) {
					byte[] blocks = Helper.byteArrayFromCompound(section, "Blocks");
					if (blocks == null) {
						continue;
					}

					Number height = Helper.numberFromCompound(section, "Y", null);
					if (height == null) {
						continue;
					}

					for (int cy = Tile.CHUNK_SIZE - 1; cy >= 0; cy--) {
						int index = cy * Tile.CHUNK_SIZE * Tile.CHUNK_SIZE + cz * Tile.CHUNK_SIZE + cx;
						if (!isEmpty(blocks[index])) {
							totalHeight += height.intValue() * 16 + cy;
							continue zLoop;
						}
					}
				}
			}
		}
		return totalHeight / (Tile.CHUNK_SIZE * Tile.CHUNK_SIZE);
	}

	private boolean isEmpty(int blockID) {
		return blockID == 0 || blockID == 166 || blockID == 217;
	}

	private int filterSections(CompoundTag sectionA, CompoundTag sectionB) {
		return Helper.numberFromCompound(sectionB, "Y", -1).intValue() - Helper.numberFromCompound(sectionA, "Y", -1).intValue();
	}

	@Override
	public int getBlockAmount(CompoundTag data, String[] blocks) {
		ListTag<CompoundTag> sections = Helper.tagFromLevelFromRoot(data, "Sections", null);
		if (sections == null) {
			return 0;
		}

		int result = 0;

		// map block names to block ids
		for (String blockName : blocks) {
			if (!mapping.containsKey(blockName)) {
				continue;
			}

			BlockData[] blockData = mapping.get(blockName);

			for (CompoundTag section : sections) {
				byte[] blockIDs = Helper.byteArrayFromCompound(section, "Blocks");
				if (blockIDs == null) {
					continue;
				}

				byte[] blockIDsData = Helper.byteArrayFromCompound(section, "Data");
				if (blockIDsData == null) {
					continue;
				}

				for (int i = 0; i < blockIDs.length; i++) {
					int blockID = blockIDs[i] & 0xFF;
					byte dataByte = blockIDsData[i / 2];
					byte dataBits = (byte) (i % 2 == 0 ? dataByte & 0x0F : (dataByte >> 4) & 0x0F);
					for (BlockData bd : blockData) {
						if (blockID == bd.id && bd.data.contains(dataBits)) {
							result++;
						}
					}
				}
			}
		}
		return result;
	}

	@Override
	public ListTag<CompoundTag> getTileEntities(CompoundTag data) {
		return Helper.tagFromLevelFromRoot(data, "TileEntities");
	}

	@Override
	public CompoundTag getStructureReferences(CompoundTag data) {
		CompoundTag structures = Helper.tagFromLevelFromRoot(data, "Structures");
		return Helper.tagFromCompound(structures, "References");
	}

	@Override
	public CompoundTag getStructureStarts(CompoundTag data) {
		CompoundTag structures = Helper.tagFromLevelFromRoot(data, "Structures");
		return Helper.tagFromCompound(structures, "Starts");
	}

	@Override
	public ListTag<CompoundTag> getSections(CompoundTag data) {
		return Helper.tagFromLevelFromRoot(data, "Sections");
	}

	@Override
	public LongTag getInhabitedTime(CompoundTag data) {
		return Helper.tagFromLevelFromRoot(data, "InhabitedTime");
	}

	@Override
	public void setInhabitedTime(CompoundTag data, long inhabitedTime) {
		CompoundTag level = Helper.levelFromRoot(data);
		if (level != null) {
			level.putLong("InhabitedTime", inhabitedTime);
		}
	}

	@Override
	public StringTag getStatus(CompoundTag data) {
		return Helper.tagFromLevelFromRoot(data, "Status");
	}

	@Override
	public void setStatus(CompoundTag data, String status) {
		CompoundTag level = Helper.levelFromRoot(data);
		if (level != null) {
			level.putString("Status", status);
		}
	}

	@Override
	public LongTag getLastUpdate(CompoundTag data) {
		return Helper.tagFromLevelFromRoot(data, "LastUpdate");
	}

	@Override
	public void setLastUpdate(CompoundTag data, long lastUpdate) {
		CompoundTag level = Helper.levelFromRoot(data);
		if (level != null) {
			level.putLong("Status", lastUpdate);
		}
	}

	@Override
	public IntTag getXPos(CompoundTag data) {
		return Helper.tagFromLevelFromRoot(data, "xPos");
	}

	@Override
	public IntTag getYPos(CompoundTag data) {
		return null;
	}

	@Override
	public IntTag getZPos(CompoundTag data) {
		return Helper.tagFromLevelFromRoot(data, "zPos");
	}

	@Override
	public ByteTag getLightPopulated(CompoundTag data) {
		return Helper.tagFromLevelFromRoot(data, "LightPopulated");
	}

	@Override
	public void setLightPopulated(CompoundTag data, byte lightPopulated) {
		CompoundTag level = Helper.levelFromRoot(data);
		if (level != null) {
			level.putLong("LightPopulated", lightPopulated);
		}
	}

	@Override
	public void forceBlending(CompoundTag data) {
		// do nothing
	}
}

package net.querz.mcaselector.version.anvil118;

import net.querz.mcaselector.math.Bits;
import net.querz.mcaselector.math.MathUtil;
import net.querz.mcaselector.tiles.Tile;
import net.querz.mcaselector.version.ChunkRenderer;
import net.querz.mcaselector.version.ColorMapping;
import net.querz.mcaselector.version.Helper;
import net.querz.nbt.tag.*;
import static net.querz.mcaselector.validation.ValidationHelper.silent;

public class Anvil118ChunkRenderer implements ChunkRenderer {

	@Override
	public void drawChunk(CompoundTag root, ColorMapping colorMapping, int x, int z, int scale, int[] pixelBuffer, int[] waterPixels, short[] terrainHeights, short[] waterHeights, boolean water, int height) {
		Integer dataVersion = Helper.intFromCompound(root, "DataVersion");
		if (dataVersion == null) {
			return;
		}

		ListTag<CompoundTag> sections = LegacyHelper.getSections(root, dataVersion);
		if (sections == null) {
			return;
		}

		int absHeight = height + 64;

		@SuppressWarnings("unchecked")
		ListTag<CompoundTag>[] palettes = (ListTag<CompoundTag>[]) new ListTag[24];
		long[][] blockStatesArray = new long[24][];
		@SuppressWarnings("unchecked")
		ListTag<StringTag>[] biomePalettes = (ListTag<StringTag>[]) new ListTag[24];
		long[][] biomesArray = new long[24][];
		sections.forEach(s -> {
			ListTag<CompoundTag> p = LegacyHelper.getPalette(s, dataVersion);
			long[] b = LegacyHelper.getBlockStates(s, dataVersion);

			int y = Helper.numberFromCompound(s, "Y", -5).intValue();
			if (y >= -4 && y < 20 && p != null) {
				palettes[y + 4] = p;
				blockStatesArray[y + 4] = b;

				if (dataVersion >= 2834) {
					biomePalettes[y + 4] = Helper.tagFromCompound(Helper.tagFromCompound(s, "biomes"), "palette");
					biomesArray[y + 4] = Helper.longArrayFromCompound(Helper.tagFromCompound(s, "biomes"), "data");
				}
			}
		});

		int[] biomes = LegacyHelper.getLegacyBiomes(root, dataVersion);

		for (int cx = 0; cx < Tile.CHUNK_SIZE; cx += scale) {
			zLoop:
			for (int cz = 0; cz < Tile.CHUNK_SIZE; cz += scale) {

				//loop over sections
				boolean waterDepth = false;
				for (int i = palettes.length - (24 - (absHeight >> 4)); i >= 0; i--) {
					ListTag<CompoundTag> palette = palettes[i];
					if (palette == null) {
						continue;
					}
					long[] blockStates = blockStatesArray[i];

					int sectionHeight = (i - 4) * Tile.CHUNK_SIZE;

					int bits = blockStates == null ? 0 : blockStates.length >> 6;
					int clean = ((int) Math.pow(2, bits) - 1);

					long[] biomeIndices = biomesArray[i];
					ListTag<StringTag> biomesPalette = biomePalettes[i];

					int biomeBits = 1;
					if (biomesPalette != null) {
						biomeBits = 32 - Bits.fastNumberOfLeadingZeroes(Math.max(biomesPalette.size() - 1, 1));
					}

					int startHeight;
					if (absHeight >> 4 == i) {
						startHeight = Tile.CHUNK_SIZE - (16 - absHeight % 16);
					} else {
						startHeight = Tile.CHUNK_SIZE - 1;
					}

					for (int cy = startHeight; cy >= 0; cy--) {
						int paletteIndex = getPaletteIndex(getIndex(cx, cy, cz), blockStates, bits, clean);
						CompoundTag blockData = palette.get(paletteIndex);

						if (isEmpty(blockData)) {
							continue;
						}

						int biomeLegacy = -1;
						String biome = "";
						if (dataVersion >= 2834) {
							biome = getBiomeAtBlock(biomeIndices, biomesPalette, cx, cy, cz, biomeBits);
						} else {
							biomeLegacy = getBiomeAtBlock(biomes, cx, sectionHeight + cy, cz);
							biomeLegacy = MathUtil.clamp(biomeLegacy, 0, 255);
						}

						int regionIndex = (z + cz / scale) * (Tile.SIZE / scale) + (x + cx / scale);
						if (water) {
							if (!waterDepth) {
								pixelBuffer[regionIndex] = dataVersion >= 2834 ? colorMapping.getRGB(blockData, biome) : colorMapping.getRGB(blockData, biomeLegacy); // water color
								waterHeights[regionIndex] = (short) (sectionHeight + cy); // height of highest water or terrain block
							}
							if (isWater(blockData)) {
								waterDepth = true;
								continue;
							} else if (isWaterlogged(blockData)) {
								pixelBuffer[regionIndex] = dataVersion >= 2834 ? colorMapping.getRGB(waterDummy, biome) : colorMapping.getRGB(waterDummy, biomeLegacy); // water color
								waterPixels[regionIndex] = dataVersion >= 2834 ? colorMapping.getRGB(blockData, biome) : colorMapping.getRGB(blockData, biomeLegacy); // color of waterlogged block
								waterHeights[regionIndex] = (short) (sectionHeight + cy);
								terrainHeights[regionIndex] = (short) (sectionHeight + cy - 1); // "height" of bottom of water, which will just be 1 block lower so shading works
								continue zLoop;
							} else {
								waterPixels[regionIndex] = dataVersion >= 2834 ? colorMapping.getRGB(blockData, biome) : colorMapping.getRGB(blockData, biomeLegacy); // color of block at bottom of water
							}
						} else {
							pixelBuffer[regionIndex] = dataVersion >= 2834 ? colorMapping.getRGB(blockData, biome) : colorMapping.getRGB(blockData, biomeLegacy);
						}
						terrainHeights[regionIndex] = (short) (sectionHeight + cy); // height of bottom of water
						continue zLoop;
					}
				}
			}
		}
	}

	@Override
	public void drawLayer(CompoundTag root, ColorMapping colorMapping, int x, int z, int scale, int[] pixelBuffer, int height) {
		Integer dataVersion = Helper.intFromCompound(root, "DataVersion");
		if (dataVersion == null) {
			return;
		}

		ListTag<CompoundTag> sections = LegacyHelper.getSections(root, dataVersion);
		if (sections == null) {
			return;
		}

		CompoundTag section = null;
		for (CompoundTag s : sections) {
			int y = Helper.numberFromCompound(s, "Y", -5).intValue();
			if (y == height >> 4) {
				section = s;
				break;
			}
		}
		if (section == null) {
			return;
		}

		ListTag<StringTag> biomesPalette = null;
		long[] biomeIndices = null;

		ListTag<CompoundTag> palette = LegacyHelper.getPalette(section, dataVersion);
		long[] blockStates = LegacyHelper.getBlockStates(section, dataVersion);
		if (palette == null) {
			return;
		}

		if (dataVersion >= 2834) {
			biomesPalette = Helper.tagFromCompound(Helper.tagFromCompound(section, "biomes"), "palette");
			biomeIndices = Helper.longArrayFromCompound(Helper.tagFromCompound(section, "biomes"), "data");
		}

		int[] biomes = LegacyHelper.getLegacyBiomes(root, dataVersion);

		height = height + 64;

		int cy = height % 16;
		int bits = blockStates == null ? 0 : blockStates.length >> 6;
		int clean = ((int) Math.pow(2, bits) - 1);

		int biomeBits = 1;
		if (biomesPalette != null) {
			biomeBits = 32 - Bits.fastNumberOfLeadingZeroes(Math.max(biomesPalette.size() - 1, 1));
		}

		for (int cx = 0; cx < Tile.CHUNK_SIZE; cx += scale) {
			for (int cz = 0; cz < Tile.CHUNK_SIZE; cz += scale) {
				int paletteIndex = getPaletteIndex(getIndex(cx, cy, cz), blockStates, bits, clean);
				CompoundTag blockData = palette.get(paletteIndex);

				if (isEmpty(blockData)) {
					continue;
				}

				int biomeLegacy = -1;
				String biome = "";
				if (dataVersion >= 2834) {
					biome = getBiomeAtBlock(biomeIndices, biomesPalette, cx, cy, cz, biomeBits);
				} else {
					biomeLegacy = getBiomeAtBlock(biomes, cx, height, cz);
					biomeLegacy = MathUtil.clamp(biomeLegacy, 0, 255);
				}


				int regionIndex = (z + cz / scale) * (Tile.SIZE / scale) + (x + cx / scale);
				pixelBuffer[regionIndex] = dataVersion >= 2834 ? colorMapping.getRGB(blockData, biome) : colorMapping.getRGB(blockData, biomeLegacy);
			}
		}
	}

	@Override
	public void drawCaves(CompoundTag root, ColorMapping colorMapping, int x, int z, int scale, int[] pixelBuffer, short[] terrainHeights, int height) {
		Integer dataVersion = Helper.intFromCompound(root, "DataVersion");
		if (dataVersion == null) {
			return;
		}

		ListTag<CompoundTag> sections = LegacyHelper.getSections(root, dataVersion);
		if (sections == null) {
			return;
		}

		int absHeight = height + 64;

		@SuppressWarnings("unchecked")
		ListTag<CompoundTag>[] palettes = (ListTag<CompoundTag>[]) new ListTag[24];
		long[][] blockStatesArray = new long[24][];
		@SuppressWarnings("unchecked")
		ListTag<StringTag>[] biomePalettes = (ListTag<StringTag>[]) new ListTag[24];
		long[][] biomesArray = new long[24][];
		sections.forEach(s -> {
			ListTag<CompoundTag> p = LegacyHelper.getPalette(s, dataVersion);
			long[] b = LegacyHelper.getBlockStates(s, dataVersion);

			int y = Helper.numberFromCompound(s, "Y", -5).intValue();
			if (y >= -4 && y < 20 && p != null) {
				palettes[y + 4] = p;
				blockStatesArray[y + 4] = b;

				if (dataVersion >= 2834) {
					biomePalettes[y + 4] = Helper.tagFromCompound(Helper.tagFromCompound(s, "biomes"), "palette");
					biomesArray[y + 4] = Helper.longArrayFromCompound(Helper.tagFromCompound(s, "biomes"), "data");
				}
			}
		});

		int[] biomes = LegacyHelper.getLegacyBiomes(root, dataVersion);

		for (int cx = 0; cx < Tile.CHUNK_SIZE; cx += scale) {
			zLoop:
			for (int cz = 0; cz < Tile.CHUNK_SIZE; cz += scale) {

				int ignored = 0;
				boolean doneSkipping = false;

				// loop over sections
				for (int i = palettes.length - (24 - (absHeight >> 4)); i >= 0; i--) {
					ListTag<CompoundTag> palette = palettes[i];
					if (palette == null) {
						continue;
					}
					long[] blockStates = blockStatesArray[i];

					int sectionHeight = (i - 4) * Tile.CHUNK_SIZE;

					int bits = blockStates == null ? 0 : blockStates.length >> 6;
					int clean = ((int) Math.pow(2, bits) - 1);

					long[] biomeIndices = biomesArray[i];
					ListTag<StringTag> biomesPalette = biomePalettes[i];

					int biomeBits = 1;
					if (biomesPalette != null) {
						biomeBits = 32 - Bits.fastNumberOfLeadingZeroes(Math.max(biomesPalette.size() - 1, 1));
					}

					int startHeight;
					if (absHeight >> 4 == i) {
						startHeight = Tile.CHUNK_SIZE - (16 - absHeight % 16);
					} else {
						startHeight = Tile.CHUNK_SIZE - 1;
					}

					for (int cy = startHeight; cy >= 0; cy--) {
						int paletteIndex = getPaletteIndex(getIndex(cx, cy, cz), blockStates, bits, clean);
						CompoundTag blockData = palette.get(paletteIndex);

						if (!isEmptyOrFoliage(blockData, colorMapping)) {
							if (doneSkipping) {
								int regionIndex = (z + cz / scale) * (Tile.SIZE / scale) + (x + cx / scale);

								int biomeLegacy = -1;
								String biome = "";
								if (dataVersion >= 2834) {
									biome = getBiomeAtBlock(biomeIndices, biomesPalette, cx, cy, cz, biomeBits);
								} else {
									biomeLegacy = getBiomeAtBlock(biomes, cx, sectionHeight + cy, cz);
									biomeLegacy = MathUtil.clamp(biomeLegacy, 0, 255);
								}

								pixelBuffer[regionIndex] = dataVersion >= 2834 ? colorMapping.getRGB(blockData, biome) : colorMapping.getRGB(blockData, biomeLegacy);
								terrainHeights[regionIndex] = (short) (sectionHeight + cy);
								continue zLoop;
							}
							ignored++;
						} else if (ignored > 0) {
							doneSkipping = true;
						}
					}
				}
			}
		}
	}

	@Override
	public CompoundTag minimizeChunk(CompoundTag root) {
		Integer dataVersion = Helper.intFromCompound(root, "DataVersion");
		if (dataVersion == null) {
			return root;
		}

		CompoundTag minData = new CompoundTag();
		minData.put("DataVersion", root.get("DataVersion").clone());

		if (dataVersion > 2843) {
			minData.put("sections", root.get("sections").clone());
			minData.put("Status", root.get("Status").clone());
		} else {
			CompoundTag level = new CompoundTag();
			CompoundTag oldLevel = root.getCompoundTag("Level");
			if (dataVersion < 2834) {
				level.put("Biomes",oldLevel.get("Biomes").clone());
			}
			level.put("Sections", oldLevel.get("Sections").clone());
			level.put("Status", oldLevel.get("Status").clone());
			minData.put("Level", level);
		}
		return minData;
	}

	private static final CompoundTag waterDummy = new CompoundTag();

	static {
		waterDummy.putString("Name", "minecraft:water");
	}

	private boolean isWater(CompoundTag blockData) {
		return switch (Helper.stringFromCompound(blockData, "Name", "")) {
			case "minecraft:water", "minecraft:bubble_column" -> true;
			default -> false;
		};
	}

	private boolean isWaterlogged(CompoundTag data) {
		return data.get("Properties") != null && "true".equals(Helper.stringFromCompound(Helper.tagFromCompound(data, "Properties"), "waterlogged", null));
	}

	private boolean isEmpty(CompoundTag blockData) {
		return switch (Helper.stringFromCompound(blockData, "Name", "")) {
			case "minecraft:air", "minecraft:cave_air", "minecraft:barrier", "minecraft:structure_void", "minecraft:light" -> blockData.size() == 1;
			default -> false;
		};
	}

	private boolean isEmptyOrFoliage(CompoundTag blockData, ColorMapping colorMapping) {
		String name;
		return switch (name = Helper.stringFromCompound(blockData, "Name", "")) {
			case "minecraft:air", "minecraft:cave_air", "minecraft:barrier", "minecraft:structure_void", "minecraft:light", "minecraft:snow" -> blockData.size() == 1;
			default -> colorMapping.isFoliage(name);
		};
	}

	private int getIndex(int x, int y, int z) {
		return y * Tile.CHUNK_SIZE * Tile.CHUNK_SIZE + z * Tile.CHUNK_SIZE + x;
	}

	private int getBiomeIndex(int x, int y, int z) {
		return y * Tile.CHUNK_SIZE + z * 4 + x;
	}

	private int getBiomeAtBlock(int[] biomes, int biomeX, int biomeY, int biomeZ) {
		if (biomes == null) {
			return -1;
		}
		return biomes[getBiomeIndex(biomeX / 4, (biomeY + 64) / 4, biomeZ / 4)];
	}

	private String getBiomeAtBlock(long[] biomes, ListTag<StringTag> palette, int biomeX, int biomeY, int biomeZ, int bits) {
		if (palette == null) {
			return "";
		}
		if (biomes == null || biomes.length == 0) {
			return palette.get(0).getValue();
		}

		int indexesPerLong = 64 / bits;
		int biomeIndex = getBiomeIndex(biomeX >> 2 % 4, biomeY >> 2 % 4, biomeZ >> 2 % 4);
		int biomeLongIndex = biomeIndex / indexesPerLong;
		int startBit = (biomeIndex % indexesPerLong) * bits;
		return silent(() -> palette.get((int) Bits.bitRange(biomes[biomeLongIndex], startBit, startBit + bits)).getValue(), "");
	}

	private int getPaletteIndex(int index, long[] blockStates, int bits, int clean) {
		if (blockStates == null) {
			return 0;
		}
		int indicesPerLong = (int) (64D / bits);
		int blockStatesIndex = index / indicesPerLong;
		int startBit = (index % indicesPerLong) * bits;
		return (int) (blockStates[blockStatesIndex] >> startBit) & clean;
	}

}

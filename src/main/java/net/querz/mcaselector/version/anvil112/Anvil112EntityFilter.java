package net.querz.mcaselector.version.anvil112;

import net.querz.mcaselector.io.mca.ChunkData;
import net.querz.mcaselector.version.EntityFilter;
import net.querz.mcaselector.version.Helper;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;

public class Anvil112EntityFilter implements EntityFilter {

	public void deleteEntities(ChunkData data) {
		ListTag<CompoundTag> rawEntities = Helper.tagFromLevelFromRoot(data.getRegion().getData(), "Entities", null);
		if (rawEntities != null) {
			rawEntities.clear();
		}
	}

	@Override
	public ListTag<?> getEntities(ChunkData data) {
		return Helper.tagFromLevelFromRoot(data.getRegion().getData(), "Entities", null);
	}
}

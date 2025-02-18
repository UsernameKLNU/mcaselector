package net.querz.mcaselector.tiles.overlay;

import net.querz.mcaselector.io.mca.ChunkData;
import net.querz.mcaselector.version.EntityFilter;
import net.querz.mcaselector.version.VersionController;
import net.querz.nbt.tag.ListTag;

public class EntityAmountParser extends AmountParser {

	public EntityAmountParser() {
		super(OverlayType.ENTITY_AMOUNT);
	}

	@Override
	public int parseValue(ChunkData chunkData) {
		if (chunkData.getRegion() == null || chunkData.getRegion().getData() == null) {
			return 0;
		}
		EntityFilter entityFilter = VersionController.getEntityFilter(chunkData.getRegion().getData().getInt("DataVersion"));
		ListTag<?> entities = entityFilter.getEntities(chunkData);
		return entities == null ? 0 : entities.size();
	}

	@Override
	public String name() {
		return "EntityAmount";
	}
}

package ca.spottedleaf.moonrise.compat.architectury;

import dev.architectury.event.events.common.ChunkEvent;
import dev.architectury.event.events.common.EntityEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

public final class ArchitecturyHooks {
    /**
     * Invokes Architectury's ChunkEvent.SAVE_DATA event.
     *
     * @param chunkAccess The chunk that is saved.
     * @param level The level the chunk is in.
     * @param data  The data.
     */
    public static void onSaveEvent(ChunkAccess chunkAccess, ServerLevel level, SerializableChunkData data) {
        ChunkEvent.SAVE_DATA.invoker().save(chunkAccess, level, data);
    }

    /**
     * Invokes Architectury's EntityEvent.ADD event.
     *
     * @param entity The entity that is added.
     * @param level  The level the entity is in.
     * @return Whether the entity should be added.
     */
    public static boolean onEntityAdd(Entity entity, ServerLevel level) {
        return !EntityEvent.ADD.invoker().add(entity, level).isFalse();
    }
}

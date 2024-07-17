package ca.spottedleaf.moonrise.patches.chunk_tick_iteration;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;

public interface ChunkTickDistanceManager {

    public void moonrise$addPlayer(final ServerPlayer player, final SectionPos pos);

    public void moonrise$removePlayer(final ServerPlayer player, final SectionPos pos);

    public void moonrise$updatePlayer(final ServerPlayer player,
                                      final SectionPos oldPos, final SectionPos newPos,
                                      final boolean oldIgnore, final boolean newIgnore);

}

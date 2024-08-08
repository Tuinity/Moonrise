package ca.spottedleaf.moonrise.mixin.chunk_tick_iteration;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkSource;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin extends ChunkSource {

    @Shadow
    @Final
    public ServerLevel level;

    @Unique
    private ServerChunkCache.ChunkAndHolder[] iterationCopy;

    /**
     * @reason Avoid creating the list, which is sized at the chunkholder count. The actual number of ticking
     *         chunks is always lower. The mixin below will initialise the list to non-null.
     * @author Spottedleaf
     */
    @Redirect(
            method = "tickChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayListWithCapacity(I)Ljava/util/ArrayList;"
            )
    )
    private <T> ArrayList<T> avoidListCreation(final int initialArraySize) {
        return null;
    }

    /**
     * @reason Initialise the list to contain only the ticking chunks.
     * @author Spottedleaf
     */
    @ModifyVariable(
            method = "tickChunks",
            at = @At(
                    value = "STORE",
                    opcode = Opcodes.ASTORE,
                    ordinal = 0
            )
    )
    private List<ServerChunkCache.ChunkAndHolder> initTickChunks(final List<ServerChunkCache.ChunkAndHolder> shouldBeNull) {
        final ReferenceList<ServerChunkCache.ChunkAndHolder> tickingChunks =
                ((ChunkSystemServerLevel)this.level).moonrise$getTickingChunks();

        final ServerChunkCache.ChunkAndHolder[] raw = tickingChunks.getRawDataUnchecked();
        final int size = tickingChunks.size();

        if (this.iterationCopy == null || this.iterationCopy.length < size) {
            this.iterationCopy = new ServerChunkCache.ChunkAndHolder[raw.length];
        }
        System.arraycopy(raw, 0, this.iterationCopy, 0, size);

        return ObjectArrayList.wrap(
                this.iterationCopy, size
        );
    }

    /**
     * @reason Do not initialise ticking chunk list, as we did that above.
     * @author Spottedleaf
     */
    @Redirect(
            method = "tickChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;hasNext()Z",
                    ordinal = 0
            )
    )
    private <E> boolean skipTickAdd(final Iterator<E> instance) {
        return false;
    }

    /**
     * @reason Clear the iteration array, and at the same time broadcast chunk changes.
     * @author Spottedleaf
     */
    @Redirect(
            method = "tickChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
                    ordinal = 0
            )
    )
    private void broadcastChanges(final List<ServerChunkCache.ChunkAndHolder> instance,
                                  final Consumer<ServerChunkCache.ChunkAndHolder> consumer) {
        final ObjectArrayList<ServerChunkCache.ChunkAndHolder> chunks = (ObjectArrayList<ServerChunkCache.ChunkAndHolder>)instance;
        final ServerChunkCache.ChunkAndHolder[] raw = chunks.elements();
        final int size = chunks.size();

        Objects.checkFromToIndex(0, size, raw.length);
        for (int i = 0; i < size; ++i) {
            final ServerChunkCache.ChunkAndHolder holder = raw[i];
            raw[i] = null;

            holder.holder().broadcastChanges(holder.chunk());
        }
    }
}

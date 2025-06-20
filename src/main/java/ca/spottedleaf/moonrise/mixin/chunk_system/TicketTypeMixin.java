package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketType;
import net.minecraft.server.level.TicketType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;

@Mixin(TicketType.class)
abstract class TicketTypeMixin<T> implements ChunkSystemTicketType<T> {

    @Final
    @Shadow
    @Mutable
    private long timeout;

    @Unique
    private static AtomicLong ID_GENERATOR;

    /**
     * @reason Need to initialise at the start of clinit, as ticket types are constructed after.
     *         Using just the field initialiser would append the static initialiser.
     * @author Spottedleaf
     */
    @Inject(
        method = "<clinit>",
        at = @At(
            value = "HEAD"
        )
    )
    private static void initIdGenerator(final CallbackInfo ci) {
        ID_GENERATOR = new AtomicLong();
    }

    @Unique
    private final long id = ID_GENERATOR.getAndIncrement();

    @Unique
    private Comparator<T> identifierComparator;

    @Unique
    private volatile long[] counterTypes;

    @Override
    public final long moonrise$getId() {
        return this.id;
    }

    @Override
    public final Comparator<T> moonrise$getIdentifierComparator() {
        return this.identifierComparator;
    }

    @Override
    public final void moonrise$setIdentifierComparator(final Comparator<T> comparator) {
        this.identifierComparator = comparator;
    }

    @Override
    public final long[] moonrise$getCounterTypes() {
        // need to lazy init this because we cannot check if we are FORCED during construction
        final long[] types = this.counterTypes;
        if (types != null) {
            return types;
        }

        return this.counterTypes = PlatformHooks.get().getCounterTypesUncached((TicketType)(Object)this);
    }

    @Override
    public final void moonrise$setTimeout(final long to) {
        this.timeout = to;
    }
}

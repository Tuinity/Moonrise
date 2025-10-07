package ca.spottedleaf.moonrise.fabric.mixin.tick_loop;

import ca.spottedleaf.moonrise.patches.tick_loop.TickLoopPacketProcessor;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Queue;
import java.util.concurrent.locks.LockSupport;

@Mixin(PacketProcessor.class)
abstract class PacketProcessorMixin implements AutoCloseable, TickLoopPacketProcessor {

    @Shadow
    private boolean closed;

    @Shadow
    @Final
    private Queue<PacketProcessor.ListenerAndPacket<?>> packetsToBeHandled;

    @Shadow
    @Final
    private Thread runningThread;

    @Override
    public final boolean moonrise$executeSinglePacket() {
        if (this.closed) {
            return false;
        }

        final PacketProcessor.ListenerAndPacket<?> task = this.packetsToBeHandled.poll();
        if (task == null) {
            return false;
        }

        task.handle();
        return true;
    }

    /**
     * @reason Unpark the main thread to force a timely response for handling packets.
     * @author Spottedleaf
     */
    @Redirect(
        method = "scheduleIfPossible",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Queue;add(Ljava/lang/Object;)Z"
        )
    )
    private <E> boolean unparkWaitingThread(final Queue<E> instance, final E element) {
        final boolean isEmpty = instance.isEmpty();
        final boolean ret = instance.add(element);

        if (isEmpty || instance.peek() == element) {
            // only unpark if we are the first packet OR are at the head of the queue
            // we unpark if we are at the head in case the main thread emptied the queue
            // immediately before we added but after checking isEmpty
            LockSupport.unpark(this.runningThread);
        }

        return ret;
    }
}

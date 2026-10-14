package ca.spottedleaf.moonrise.mixin.nbt_rw;

import ca.spottedleaf.common.util.ThrowUtil;
import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.stream.wrapped.SimpleBufferInputStream;
import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTParser;
import ca.spottedleaf.moonrise.patches.nbt_rw.NBTConstants;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.EncoderException;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Objects;

@Mixin(FriendlyByteBuf.class)
abstract class FriendlyByteBufMixin {

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private static final String BUFFER_REF_KEY = "moonrise:network_read_nbt";

    @Unique
    private static boolean reinterpretSupported = true;

    @Unique
    private static Tag readNbtV(final ByteBuf input, final NbtAccounter accounter) throws IOException {
        final Tag ret = NbtIo.readAnyTag(new ByteBufInputStream(input), accounter);
        return ret instanceof EndTag ? null : ret;
    }

    /**
     * @reason Use faster NBT parsing
     * @author Spottedleaf
     */
    @Overwrite
    public static Tag readNbt(final ByteBuf input, final NbtAccounter accounter) {
        try {
            if (!input.hasArray() && (!input.hasMemoryAddress() || !reinterpretSupported)) {
                // just fall back to Vanilla
                return readNbtV(input, accounter);
            }

            final MemorySegment segment;
            if (input.hasArray()) {
                segment = MemorySegment.ofBuffer(ByteBuffer.wrap(input.array(), input.arrayOffset(), input.capacity())).asReadOnly();
            } else {
                try {
                    segment = MemorySegment.ofAddress(input.memoryAddress()).asReadOnly().reinterpret(input.capacity());
                } catch (final IllegalCallerException ex) {
                    reinterpretSupported = false;
                    LOGGER.error("MemorySegment.reinterpret not supported", ex);
                    return readNbtV(input, accounter);
                }
            }
            final Buffer buffer = new Buffer(
                NBTConstants.DEBUG_READ_WRITE,
                BUFFER_REF_KEY,
                segment
            );
            int vanillaReadBytes = -1;
            try {
                buffer.setIndices(input.readerIndex(), input.writerIndex());

                try (final SimpleBufferInputStream in = new SimpleBufferInputStream(buffer)) {
                    final Tag ret = FastNBTParser.parseTagNoNameVanilla(in, accounter);

                    if (NBTConstants.DEBUG_READ_WRITE) {
                        final Tag vanilla;
                        final ByteBuf inputCopy = input.copy();
                        try (final ByteBufInputStream vanillaIn = new ByteBufInputStream(inputCopy, true)) {
                            vanilla = NbtIo.readAnyTag(vanillaIn, accounter);
                            vanillaReadBytes = inputCopy.readerIndex();
                        }
                        if (!Objects.equals(vanilla, ret)) {
                            throw new IllegalStateException();
                        }
                    }
                    return ret instanceof EndTag ? null : ret;
                }
            } catch (final Throwable thr) {
                ThrowUtil.throwUnchecked(thr);
                return null;
            } finally {
                if (NBTConstants.DEBUG_READ_WRITE) {
                    if (vanillaReadBytes != -1 && buffer.getReaderIndex() != (vanillaReadBytes + input.readerIndex())) {
                        throw new IllegalStateException();
                    }
                }
                try {
                    input.readerIndex(Math.toIntExact(buffer.getReaderIndex()));
                } finally {
                    buffer.decReferenceCount(BUFFER_REF_KEY);
                    buffer.ensureRefCountZero();
                }
            }
        } catch (final IOException ex) {
            throw new EncoderException(ex);
        }
    }
}

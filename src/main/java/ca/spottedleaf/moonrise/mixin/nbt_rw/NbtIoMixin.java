package ca.spottedleaf.moonrise.mixin.nbt_rw;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;
import ca.spottedleaf.ioutil.stream.wrapped.BufferedBufferOutputStream;
import ca.spottedleaf.ioutil.stream.wrapped.SimpleBufferOutputStream;
import ca.spottedleaf.moonrise.patches.nbt_rw.CopyingOutputStream;
import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTSerialiser;
import ca.spottedleaf.moonrise.patches.nbt_rw.NBTConstants;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import static ca.spottedleaf.moonrise.patches.nbt_rw.NBTConstants.*;

@Mixin(NbtIo.class)
abstract class NbtIoMixin {

    @Unique
    private static void writeTagV(final Tag tag, final DataOutput output, final String name) throws IOException {
        final byte id = tag.getId();
        output.writeByte(id);
        if (id != NBTConstants.NBT_TAG_END) {
            if (name != null) {
                output.writeUTF(name);
            }
            tag.write(output);
        }
    }

    @Unique
    private static void writeTag(final Tag tag, final DataOutput output, final String name) throws IOException {
        final boolean stringFallback;
        final OutputStream stream;
        if (output instanceof NbtIo.StringFallbackDataOutput fallback) {
            stringFallback = true;
            stream = fallback.parent instanceof OutputStream s ? s : null;
        } else {
            stringFallback = false;
            stream = output instanceof OutputStream s ? s : null;
        }
        if (stream == null) {
            // fallback to vanilla
            writeTagV(tag, output, name);
            return;
        }
        final AbstractBufferOutputStream toFlush;
        final boolean writeBuffer;
        final CopyingOutputStream copy;

        if (stream instanceof AbstractBufferOutputStream out) {
            if (NBTConstants.DEBUG_READ_WRITE) {
                // note: no recursion, so we do not need to handle possible buffer re-use
                toFlush = new BufferedBufferOutputStream(BUFFER.get(), copy = new CopyingOutputStream(stream));
            } else {
                toFlush = out;
            }

            writeBuffer = false;
        } else {
            // note: no recursion, so we do not need to handle possible buffer re-use
            if (NBTConstants.DEBUG_READ_WRITE) {
                toFlush = new BufferedBufferOutputStream(BUFFER.get(), copy = new CopyingOutputStream(stream));
            } else {
                toFlush = new BufferedBufferOutputStream(BUFFER.get(), stream);
            }
            writeBuffer = true;
        }
        try {
            if (name != null) {
                FastNBTSerialiser.writeNamedTag(tag, name, toFlush, stringFallback);
            } else {
                FastNBTSerialiser.writeTag(tag, toFlush, stringFallback);
            }

            if (NBTConstants.DEBUG_READ_WRITE) {
                toFlush.writeBuffer();

                final ByteArrayOutputStream vanillaRaw = new ByteArrayOutputStream();
                writeTagV(tag, new DataOutputStream(vanillaRaw), name);

                final byte[] expected = vanillaRaw.toByteArray();
                final byte[] got = new byte[Math.toIntExact(copy.copy.getReadableBytes())];
                copy.copy.readBytes(0L, got, 0, got.length);

                final int mismatch = Arrays.mismatch(expected, got);
                if (mismatch != -1) {
                    // this will fail to write the byte at the mismatch
                    final SimpleBufferOutputStream fail = new SimpleBufferOutputStream(new Buffer(false, "", new byte[mismatch]));
                    if (name != null) {
                        FastNBTSerialiser.writeNamedTag(tag, name, fail, stringFallback);
                    } else {
                        FastNBTSerialiser.writeTag(tag, fail, stringFallback);
                    }

                    throw new RuntimeException("????");
                }
            }
        } finally {
            // maintain behaviour with DataOutput
            if (writeBuffer) {
                toFlush.writeBuffer();
            }
        }
    }

    /**
     * @reason Use faster NBT serializing
     * @author Spottedleaf
     */
    @Overwrite
    public static void writeAnyTag(final Tag tag, final DataOutput output) throws IOException {
        writeTag(tag, output, null);
    }

    /**
     * @reason Use faster NBT serializing
     * @author Spottedleaf
     */
    @Overwrite
    public static void writeUnnamedTag(final Tag tag, DataOutput output) throws IOException {
        writeTag(tag, output, "");
    }
}

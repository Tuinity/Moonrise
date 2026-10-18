package ca.spottedleaf.moonrise.patches.nbt_rw;

import ca.spottedleaf.ioutil.stream.AbstractBufferInputStream;
import ca.spottedleaf.ioutil.stream.wrapped.BufferedBufferInputStream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtFormatException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static ca.spottedleaf.moonrise.patches.nbt_rw.NBTConstants.*;

public final class FastNBTParser {

    private static void skipUTF(final AbstractBufferInputStream in) throws IOException {
        in.skipNBytes((long)in.readUnsignedShort());
    }

    private static String readUTF(final AbstractBufferInputStream in) throws IOException {
        return in.readUTF();
    }

    public static CompoundTag nbtIoRead(final DataInput dataIn) throws IOException {
        if (!(dataIn instanceof InputStream stream)) {
            // fallback to vanilla
            return NbtIo.read(dataIn);
        }
        return nbtIoReadStream(stream, NbtAccounter.unlimitedHeap());
    }

    public static CompoundTag nbtIoReadStream(final InputStream stream, final NbtAccounter ctx) throws IOException {
        final Tag tag;

        final AbstractBufferInputStream toRead;
        final CopyingInputStream copy;
        if (stream instanceof AbstractBufferInputStream in) {
            if (DEBUG_READ_WRITE) {
                // note: no recursion, so we do not need to handle possible buffer re-use
                toRead = new BufferedBufferInputStream(BUFFER.get(), copy = new CopyingInputStream(stream));
            } else {
                toRead = in;
            }
        } else {
            // note: no recursion, so we do not need to handle possible buffer re-use
            if (DEBUG_READ_WRITE) {
                toRead = new BufferedBufferInputStream(BUFFER.get(), copy = new CopyingInputStream(stream));
            } else {
                toRead = new BufferedBufferInputStream(BUFFER.get(), stream);
            }
        }

        tag = FastNBTParser.parseNamedTagVanilla(toRead, ctx);

        if (DEBUG_READ_WRITE) {
            final byte[] vanillaSrc = new byte[Math.toIntExact(copy.copy.getReadableBytes())];
            copy.copy.readBytes(0L, vanillaSrc, 0, vanillaSrc.length);
            final Tag vanilla = NbtIo.read(new DataInputStream(new ByteArrayInputStream(vanillaSrc)));

            if (!Objects.equals(tag, vanilla)) {
                throw new IllegalStateException();
            }
        }

        if (tag instanceof CompoundTag ret) {
            return ret;
        }

        throw new IOException("Root tag must be a named compound tag");
    }

    // matches Vanilla's NbtIo#readTagSafe: only IOExceptions thrown while reading the tag payload are wrapped,
    // while those thrown reading the tag type or name propagate unchanged
    private static Tag parseVanillaSafe(final byte id, final AbstractBufferInputStream in, final NbtAccounter ctx) {
        try {
            return parseVanilla(id, in, ctx);
        } catch (final IOException ex) {
            final CrashReport report = CrashReport.forThrowable(ex, "Loading NBT data");
            final CrashReportCategory category = report.addCategory("NBT Tag");
            category.setDetail("Tag type", id);
            throw new ReportedNbtException(report);
        }
    }

    public static Tag parseNamedTagVanilla(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        final byte id = in.readByte();
        if (id == NBT_TAG_END) {
            return EndTag.INSTANCE;
        }
        skipUTF(in);
        return parseVanillaSafe(id, in, ctx);
    }

    public static Tag parseTagNoNameVanilla(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        final byte id = in.readByte();
        if (id == NBT_TAG_END) {
            return EndTag.INSTANCE;
        }
        return parseVanillaSafe(id, in, ctx);
    }

    private static ByteTag parseByteTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.accountBytes(ByteTag.SELF_SIZE_IN_BYTES);
        return ByteTag.valueOf(in.readByte());
    }

    private static ShortTag parseShortTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.accountBytes(ShortTag.SELF_SIZE_IN_BYTES);
        return ShortTag.valueOf(in.readShort());
    }

    private static IntTag parseIntTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.accountBytes(IntTag.SELF_SIZE_IN_BYTES);
        return IntTag.valueOf(in.readInt());
    }

    private static LongTag parseLongTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.accountBytes(LongTag.SELF_SIZE_IN_BYTES);
        return LongTag.valueOf(in.readLong());
    }

    private static FloatTag parseFloatTagTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.accountBytes(FloatTag.SELF_SIZE_IN_BYTES);
        return FloatTag.valueOf(in.readFloat());
    }

    private static DoubleTag parseDoubleTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.accountBytes(DoubleTag.SELF_SIZE_IN_BYTES);
        return DoubleTag.valueOf(in.readDouble());
    }

    private static ByteArrayTag parseByteArrayTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.accountBytes(ByteArrayTag.SELF_SIZE_IN_BYTES);
        final int len = in.readInt();
        ctx.accountBytes(Byte.BYTES, (long)len);
        final byte[] ret = new byte[len];
        in.readFully(ret);
        return new ByteArrayTag(ret);
    }

    private static IntArrayTag parseIntArrayTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.accountBytes(IntArrayTag.SELF_SIZE_IN_BYTES);
        final int len = in.readInt();
        ctx.accountBytes(Integer.BYTES, (long)len);
        final int[] ret = new int[len];
        in.readFully(ret);
        return new IntArrayTag(ret);
    }

    private static LongArrayTag parseLongArrayTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.accountBytes(LongArrayTag.SELF_SIZE_IN_BYTES);
        final int len = in.readInt();
        ctx.accountBytes(Long.BYTES, (long)len);
        final long[] ret = new long[len];
        in.readFully(ret);
        return new LongArrayTag(ret);
    }

    private static StringTag parseStringTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.accountBytes(StringTag.SELF_SIZE_IN_BYTES);
        final String ret = readUTF(in);
        ctx.accountBytes(2L, (long)ret.length());
        return StringTag.valueOf(ret);
    }

    private static ListTag parseListTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.pushDepth();
        try {
            ctx.accountBytes(ListTag.SELF_SIZE_IN_BYTES);
            final byte listType = in.readByte();
            final int listLen = in.readInt();
            if (listLen < 0) {
                throw new NbtFormatException("Negative ListTag count: " + listLen);
            }
            if (listType == 0 && listLen != 0) {
                throw new NbtFormatException("Type cannot be END on list with non-zero count");
            }
            ctx.accountBytes(Integer.BYTES, (long)listLen);

            final ArrayList<Tag> ret = new ArrayList<>(listLen);

            if (listType == NBT_TAG_MAP) {
                for (int i = 0; i < listLen; ++i) {
                    final CompoundTag tag = parseMapTag(in, ctx);
                    final Tag unwrapped;
                    if (tag.size() == 1 && (unwrapped = tag.get("")) != null) {
                        ret.add(unwrapped);
                    } else {
                        ret.add(tag);
                    }
                }
            } else {
                for (int i = 0; i < listLen; ++i) {
                    ret.add(parseVanilla(listType, in, ctx));
                }
            }

            return new ListTag(ret);
        } finally {
            ctx.popDepth();
        }
    }

    // why is this different from StringTag...?
    private static final int MAP_STRING_SELF_SIZE = 28;

    private static final int MAP_ENTRY_SIZE = 36;


    private static CompoundTag parseMapTag(final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        ctx.pushDepth();

        try {
            ctx.accountBytes(CompoundTag.SELF_SIZE_IN_BYTES);

            final Map<String, Tag> ret = new HashMap<>();

            byte type;
            while ((type = in.readByte()) != NBT_TAG_END) {
                final String key = readUTF(in);
                ctx.accountBytes(MAP_STRING_SELF_SIZE);
                ctx.accountBytes(2L, (long)key.length());

                final Tag value = parseVanilla(type, in, ctx);
                if (null == ret.put(key, value)) {
                    ctx.accountBytes(MAP_ENTRY_SIZE);
                }
            }

            return new CompoundTag(ret);
        } finally {
            ctx.popDepth();
        }
    }

    public static Tag parseVanilla(final byte type, final AbstractBufferInputStream in, final NbtAccounter ctx) throws IOException {
        switch (type) {
            case NBT_TAG_END: {
                return EndTag.INSTANCE;
            }
            case NBT_TAG_BYTE: {
                return parseByteTag(in, ctx);
            }
            case NBT_TAG_SHORT: {
                return parseShortTag(in, ctx);
            }
            case NBT_TAG_INT: {
                return parseIntTag(in, ctx);
            }
            case NBT_TAG_LONG: {
                return parseLongTag(in, ctx);
            }
            case NBT_TAG_FLOAT: {
                return parseFloatTagTag(in, ctx);
            }
            case NBT_TAG_DOUBLE: {
                return parseDoubleTag(in, ctx);
            }
            case NBT_TAG_BYTE_ARRAY: {
                return parseByteArrayTag(in, ctx);
            }
            case NBT_TAG_STRING: {
                return parseStringTag(in, ctx);
            }
            case NBT_TAG_LIST: {
                return parseListTag(in, ctx);
            }
            case NBT_TAG_MAP: {
                return parseMapTag(in, ctx);
            }
            case NBT_TAG_INT_ARRAY: {
                return parseIntArrayTag(in, ctx);
            }
            case NBT_TAG_LONG_ARRAY: {
                return parseLongArrayTag(in, ctx);
            }

            default: {
                // matches Vanilla's TagType#createInvalid
                throw new IOException("Invalid tag id: " + type);
            }
        }
    }

    private FastNBTParser() {}
}

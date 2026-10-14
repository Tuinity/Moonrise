package ca.spottedleaf.moonrise.patches.nbt_rw;

import net.minecraft.nbt.Tag;

public final class NBTConstants {

    public static final boolean DEBUG_READ_WRITE = false;

    public static final ThreadLocal<byte[]> BUFFER = ThreadLocal.withInitial(() -> {
        return new byte[1024*32];
    });

    public static final byte NBT_TAG_END = Tag.TAG_END;
    public static final byte NBT_TAG_BYTE = Tag.TAG_BYTE;
    public static final byte NBT_TAG_SHORT = Tag.TAG_SHORT;
    public static final byte NBT_TAG_INT = Tag.TAG_INT;
    public static final byte NBT_TAG_LONG = Tag.TAG_LONG;
    public static final byte NBT_TAG_FLOAT = Tag.TAG_FLOAT;
    public static final byte NBT_TAG_DOUBLE = Tag.TAG_DOUBLE;
    public static final byte NBT_TAG_BYTE_ARRAY = Tag.TAG_BYTE_ARRAY;
    public static final byte NBT_TAG_STRING = Tag.TAG_STRING;
    public static final byte NBT_TAG_LIST = Tag.TAG_LIST;
    public static final byte NBT_TAG_MAP = Tag.TAG_COMPOUND;
    public static final byte NBT_TAG_INT_ARRAY = Tag.TAG_INT_ARRAY;
    public static final byte NBT_TAG_LONG_ARRAY = Tag.TAG_LONG_ARRAY;

    private NBTConstants() {}
}

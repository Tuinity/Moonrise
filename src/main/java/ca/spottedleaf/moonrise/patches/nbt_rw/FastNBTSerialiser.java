package ca.spottedleaf.moonrise.patches.nbt_rw;

import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Util;
import java.io.IOException;
import java.io.UTFDataFormatException;

public final class FastNBTSerialiser {

    public static void writeUTF(final String value, final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        try {
            out.writeUTF(value);
        } catch (final UTFDataFormatException ex) {
            if (!stringFallback) {
                throw ex;
            }
            Util.logAndPauseIfInIde("Failed to write NBT String", ex);
            out.writeUTF("");
        }
    }

    public static void writeTag(final Tag tag, final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        ((FastNBTWrite)(Object)tag).moonrise$writeWithId(out, stringFallback);
    }

    public static void writeUnnamedTag(final Tag tag, final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        writeNamedTag(tag, "", out, stringFallback);
    }

    public static void writeNamedTag(final Tag tag, final String name, final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        ((FastNBTWrite)(Object)tag).moonrise$writeWithIdAndName(out, name, stringFallback);
    }

    private FastNBTSerialiser() {}
}

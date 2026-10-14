package ca.spottedleaf.moonrise.mixin.nbt_rw;

import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;
import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTSerialiser;
import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTWrite;
import ca.spottedleaf.moonrise.patches.nbt_rw.NBTConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.io.IOException;
import java.util.List;

@Mixin(ListTag.class)
abstract class ListTagMixin implements FastNBTWrite {

    @Shadow
    @Final
    private List<Tag> list;

    @Shadow
    private static boolean isWrapper(final CompoundTag tag) {
        throw new UnsupportedOperationException("Implemented via mixin");
    }



    @Override
    public final void moonrise$writeWithId(final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        out.writeByte(NBTConstants.NBT_TAG_LIST);
        this.moonrise$writeWithoutId(out, stringFallback);
    }

    @Override
    public final void moonrise$writeWithIdAndName(final AbstractBufferOutputStream out, final String name, final boolean stringFallback) throws IOException {
        out.writeByte(NBTConstants.NBT_TAG_LIST);
        FastNBTSerialiser.writeUTF(name, out, stringFallback);
        this.moonrise$writeWithoutId(out, stringFallback);
    }

    @Unique
    private static byte getIdToWrite(final List<Tag> list, final int size) {
        if (size == 0) {
            return NBTConstants.NBT_TAG_END;
        }

        final Tag first = list.get(0);
        final Class<? extends Tag> firstClass = first.getClass();
        for (int i = 1; i < size; ++i) {
            if (list.get(i).getClass() != firstClass) {
                // must wrap
                return NBTConstants.NBT_TAG_MAP;
            }
        }

        // no wrapping needed
        return first.getId();
    }

    // note: equiv to 'new CompoundTag(Map.of("", tag)).write(...)'
    @Unique
    private static void writeWrapped(final Tag tag, final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        FastNBTSerialiser.writeNamedTag(tag, "", out, stringFallback);

        out.writeByte(NBTConstants.NBT_TAG_END);
    }

    @Override
    public final void moonrise$writeWithoutId(final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        final List<Tag> tags = this.list;
        final int size = tags.size();

        final byte typeToWrite = getIdToWrite(tags, size);

        out.writeByte(typeToWrite);
        out.writeInt(size);

        if (typeToWrite == NBTConstants.NBT_TAG_MAP) {
            // must potentially wrap
            for (int i = 0; i < size; ++i) {
                final Tag tag = tags.get(i);
                if (!(tag instanceof CompoundTag compoundTag) || isWrapper(compoundTag)) {
                    writeWrapped(tag, out, stringFallback);
                } else {
                    ((FastNBTWrite)(Object)compoundTag).moonrise$writeWithoutId(out, stringFallback);
                }
            }
        } else {
            for (int i = 0; i < size; ++i) {
                ((FastNBTWrite)(Object)tags.get(i)).moonrise$writeWithoutId(out, stringFallback);
            }
        }
    }
}

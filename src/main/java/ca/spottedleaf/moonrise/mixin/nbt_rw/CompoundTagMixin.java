package ca.spottedleaf.moonrise.mixin.nbt_rw;

import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;
import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTSerialiser;
import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTWrite;
import ca.spottedleaf.moonrise.patches.nbt_rw.NBTConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Mixin(CompoundTag.class)
abstract class CompoundTagMixin implements FastNBTWrite {

    @Shadow
    @Final
    private Map<String, Tag> tags;

    @Override
    public final void moonrise$writeWithId(final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        out.writeByte(NBTConstants.NBT_TAG_MAP);
        this.moonrise$writeWithoutId(out, stringFallback);
    }

    @Override
    public final void moonrise$writeWithIdAndName(final AbstractBufferOutputStream out, final String name, final boolean stringFallback) throws IOException {
        out.writeByte(NBTConstants.NBT_TAG_MAP);
        FastNBTSerialiser.writeUTF(name, out, stringFallback);
        this.moonrise$writeWithoutId(out, stringFallback);
    }

    // note: on change, check ListTagMixin#writeWrapped
    @Override
    public final void moonrise$writeWithoutId(final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        if (!this.tags.isEmpty()) {
            for (final Map.Entry<String, Tag> entry : this.tags.entrySet()) {
                FastNBTSerialiser.writeNamedTag(entry.getValue(), entry.getKey(), out, stringFallback);
            }
        }

        out.writeByte(NBTConstants.NBT_TAG_END);
    }

    /**
     * @reason Pre-size the HashMap
     * @author Spottedleaf
     */
    @Overwrite
    public CompoundTag copy() {
        if (this.tags.isEmpty()) {
            return new CompoundTag();
        }

        final HashMap<String, Tag> ret = HashMap.newHashMap(this.tags.size());

        for (final Map.Entry<String, Tag> entry : this.tags.entrySet()) {
            ret.put(entry.getKey(), entry.getValue().copy());
        }

        return new CompoundTag(ret);
    }
}

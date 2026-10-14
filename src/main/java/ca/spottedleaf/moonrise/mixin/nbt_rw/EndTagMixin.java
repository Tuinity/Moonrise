package ca.spottedleaf.moonrise.mixin.nbt_rw;

import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;
import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTWrite;
import ca.spottedleaf.moonrise.patches.nbt_rw.NBTConstants;
import net.minecraft.nbt.EndTag;
import org.spongepowered.asm.mixin.Mixin;
import java.io.IOException;

@Mixin(EndTag.class)
abstract class EndTagMixin implements FastNBTWrite {

    @Override
    public final void moonrise$writeWithId(final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        out.writeByte(NBTConstants.NBT_TAG_END);
    }

    @Override
    public final void moonrise$writeWithIdAndName(final AbstractBufferOutputStream out, final String name, final boolean stringFallback) throws IOException {
        out.writeByte(NBTConstants.NBT_TAG_END);
    }

    @Override
    public final void moonrise$writeWithoutId(final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {}
}

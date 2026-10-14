package ca.spottedleaf.moonrise.mixin.nbt_rw;

import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;
import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTSerialiser;
import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTWrite;
import ca.spottedleaf.moonrise.patches.nbt_rw.NBTConstants;
import net.minecraft.nbt.ShortTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.io.IOException;

@Mixin(ShortTag.class)
abstract class ShortTagMixin implements FastNBTWrite {

    @Shadow
    @Final
    private short value;

    @Override
    public final void moonrise$writeWithId(final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        out.writeByte(NBTConstants.NBT_TAG_SHORT);
        this.moonrise$writeWithoutId(out, stringFallback);
    }

    @Override
    public final void moonrise$writeWithIdAndName(final AbstractBufferOutputStream out, final String name, final boolean stringFallback) throws IOException {
        out.writeByte(NBTConstants.NBT_TAG_SHORT);
        FastNBTSerialiser.writeUTF(name, out, stringFallback);
        this.moonrise$writeWithoutId(out, stringFallback);
    }

    @Override
    public final void moonrise$writeWithoutId(final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException {
        out.writeShort((int)this.value);
    }
}

package ca.spottedleaf.moonrise.mixin.nbt_rw;

import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTWrite;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Tag.class)
interface TagMixin extends FastNBTWrite {
}

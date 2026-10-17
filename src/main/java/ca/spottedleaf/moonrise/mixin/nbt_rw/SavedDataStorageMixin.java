package ca.spottedleaf.moonrise.mixin.nbt_rw;

import ca.spottedleaf.moonrise.patches.nbt_rw.FastNBTParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.io.DataInput;
import java.io.IOException;

@Mixin(SavedDataStorage.class)
abstract class SavedDataStorageMixin {

    /**
     * @reason Use faster NBT parsing
     * @author Spottedleaf
     */
    @Redirect(
        method = "readTagFromDisk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/nbt/NbtIo;read(Ljava/io/DataInput;)Lnet/minecraft/nbt/CompoundTag;"
        )
    )
    private CompoundTag useFasterRead(final DataInput input) throws IOException {
        return FastNBTParser.nbtIoRead(input);
    }
}

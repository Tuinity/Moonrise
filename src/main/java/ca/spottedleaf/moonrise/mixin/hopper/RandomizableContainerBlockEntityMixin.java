package ca.spottedleaf.moonrise.mixin.hopper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;

@Mixin(RandomizableContainerBlockEntity.class)
public abstract class RandomizableContainerBlockEntityMixin extends BaseContainerBlockEntity implements RandomizableContainer {

    @Shadow
    protected abstract NonNullList<ItemStack> getItems();

    protected RandomizableContainerBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    /**
     * @reason Remove streams
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public boolean isEmpty() {
        this.unpackLootTable(null);

        final List<ItemStack> items = this.getItems();

        for (int i = 0, len = items.size(); i < len; ++i) {
            if (!items.get(i).isEmpty()) {
                return false;
            }
        }

        return true;
    }
}

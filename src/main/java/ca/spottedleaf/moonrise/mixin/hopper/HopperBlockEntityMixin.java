package ca.spottedleaf.moonrise.mixin.hopper;

import ca.spottedleaf.moonrise.patches.chunk_getblock.GetBlockChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin extends RandomizableContainerBlockEntity implements Hopper {

    @Shadow
    private NonNullList<ItemStack> items;

    @Shadow
    protected abstract boolean isOnCooldown();

    @Shadow
    protected abstract void setCooldown(int i);

    @Shadow
    private static Container getSourceContainer(Level level, Hopper hopper) {
        return null;
    }

    @Shadow
    public static boolean addItem(Container container, ItemEntity itemEntity) {
        return false;
    }

    @Shadow
    private static boolean tryTakeInItemFromSlot(Hopper hopper, Container container, int i, Direction direction) {
        return false;
    }

    @Shadow
    private static boolean ejectItems(Level level, BlockPos blockPos, BlockState blockState, Container container) {
        return false;
    }

    protected HopperBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Unique
    private static final AABB HOPPER_ITEM_SUCK_OVERALL = Hopper.SUCK.bounds();
    @Unique
    private static final AABB[] HOPPER_ITEM_SUCK_INDIVIDUAL = Hopper.SUCK.toAabbs().toArray(new AABB[0]);

    /**
     * @reason Multiple getEntities calls + the streams here are bad. We can
     * use the overall suck shape AABB for the getEntities call, and check the individual shapes in the predicate.
     * The suck shape is static, so we can cache the overall AABB and the individual AABBs.
     * @author Spottedleaf
     */
    @Overwrite
    public static List<ItemEntity> getItemsAtAndAbove(final Level level, final Hopper hopper) {
        final VoxelShape suckShape = hopper.getSuckShape();
        if (suckShape == Hopper.SUCK) { // support custom mod shapes (????)
            final double shiftX = hopper.getLevelX() - 0.5D;
            final double shiftY = hopper.getLevelY() - 0.5D;
            final double shiftZ = hopper.getLevelZ() - 0.5D;
            return level.getEntitiesOfClass(ItemEntity.class, HOPPER_ITEM_SUCK_OVERALL.move(shiftX, shiftY, shiftZ), (final Entity entity) -> {
                if (!entity.isAlive()) { // EntitySelector.ENTITY_STILL_ALIVE
                    return false;
                }

                for (final AABB aabb : HOPPER_ITEM_SUCK_INDIVIDUAL) {
                    if (aabb.move(shiftX, shiftY, shiftZ).intersects(entity.getBoundingBox())) {
                        return true;
                    }
                }

                return false;
            });
        } else {
            return getItemsAtAndAboveSlow(level, hopper, suckShape);
        }
    }

    @Unique
    private static List<ItemEntity> getItemsAtAndAboveSlow(Level level, Hopper hopper, VoxelShape suckShape) {
        final double shiftX = hopper.getLevelX() - 0.5D;
        final double shiftY = hopper.getLevelY() - 0.5D;
        final double shiftZ = hopper.getLevelZ() - 0.5D;

        suckShape = suckShape.move(shiftX, shiftY, shiftZ);

        final List<AABB> individual = suckShape.toAabbs();

        return level.getEntitiesOfClass(ItemEntity.class, suckShape.bounds(), (final Entity entity) -> {
            if (!entity.isAlive()) { // EntitySelector.ENTITY_STILL_ALIVE
                return false;
            }

            for (final AABB aabb : individual) {
                if (aabb.intersects(entity.getBoundingBox())) {
                    return true;
                }
            }

            return false;
        });
    }

    /**
     * @reason Forces a return false when container is not null so that the below mixin can inject its own return logic.
     * @author Spottedleaf
     */
    @Redirect(
            method = "suckInItems",
            at = @At(
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;isEmptyContainer(Lnet/minecraft/world/Container;Lnet/minecraft/core/Direction;)Z",
                    value = "INVOKE"
            )
    )
    private static boolean forceReturnFalseForContainer(final Container container, final Direction direction) {
        return true;
    }

    /**
     * @reason Avoid checking for empty container and remove streams / indirection
     * @author Spottedleaf
     */
    @Inject(
            method = "suckInItems",
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILHARD,
            at = @At(
                    value = "RETURN",
                    ordinal = 0
            )
    )
    private static void handleContainerSuck(final Level level, final Hopper hopper, final CallbackInfoReturnable<Boolean> cir,
                                            final Container aboveContainer) {
        final Direction down = Direction.DOWN;
        // don't bother checking for empty, because the same logic can be done by trying to move items - as
        // that checks for an empty item.

        if (aboveContainer instanceof WorldlyContainer worldlyContainer) {
            for (final int slot : worldlyContainer.getSlotsForFace(down)) {
                if (tryTakeInItemFromSlot(hopper, aboveContainer, slot, down)) {
                    cir.setReturnValue(Boolean.TRUE);
                    return;
                }
            }
        } else {
            for (int slot = 0, max = aboveContainer.getContainerSize(); slot < max; ++slot) {
                if (tryTakeInItemFromSlot(hopper, aboveContainer, slot, down)) {
                    cir.setReturnValue(Boolean.TRUE);
                    return;
                }
            }
        }
        cir.setReturnValue(Boolean.FALSE);
        return;
    }

    /**
     * @reason Cache chunk for block read + TE read, and use getEntitiesOfClass to avoid looking at non-container entities
     * @author Spottedleaf
     */
    @Overwrite
    private static Container getContainerAt(final Level level, final double x, final double y, final double z) {
        final int blockX = Mth.floor(x);
        final int blockY = Mth.floor(y);
        final int blockZ = Mth.floor(z);

        final LevelChunk chunk = level.getChunk(blockX >> 4, blockZ >> 4);

        final BlockState state = ((GetBlockChunk)chunk).getBlock(blockX, blockY, blockZ);
        final Block block = state.getBlock();

        Container blockContainer = null;
        if (block instanceof WorldlyContainerHolder worldlyContainerHolder) {
            blockContainer = worldlyContainerHolder.getContainer(state, level, new BlockPos(blockX, blockY, blockZ));
        } else if (state.hasBlockEntity() && !level.isOutsideBuildHeight(blockY)) {
            final BlockPos pos = new BlockPos(blockX, blockY, blockZ);
            final BlockEntity blockEntity = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
            if (blockEntity instanceof Container) {
                blockContainer = (Container)blockEntity;
                if (block instanceof ChestBlock chestBlock && blockContainer instanceof ChestBlockEntity) {
                    blockContainer = ChestBlock.getContainer(chestBlock, state, level, pos, true);
                }
            }
        }

        if (blockContainer != null) {
            return blockContainer;
        }

        final List<Entity> containers = level.getEntitiesOfClass((Class)Container.class, new AABB(
                x - 0.5, y - 0.5, z - 0.5,
                x + 0.5, y + 0.5, z + 0.5
        ), EntitySelector.CONTAINER_ENTITY_SELECTOR);
        if (!containers.isEmpty()) {
            return (Container)containers.get(level.random.nextInt(containers.size()));
        }

        return null;
    }

    @Unique
    private static final int HOPPER_EMPTY = 0;
    @Unique
    private static final int HOPPER_HAS_ITEMS = 1;
    @Unique
    private static final int HOPPER_IS_FULL = 2;

    @Unique
    private static int getFullState(final HopperBlockEntity tileEntity) {
        tileEntity.unpackLootTable(null);

        final List<ItemStack> hopperItems = ((HopperBlockEntityMixin)(Object)tileEntity).items;

        boolean empty = true;
        boolean full = true;

        for (int i = 0, len = hopperItems.size(); i < len; ++i) {
            final ItemStack stack = hopperItems.get(i);
            if (stack.isEmpty()) {
                full = false;
                continue;
            }

            if (!full) {
                // can't be full
                return HOPPER_HAS_ITEMS;
            }

            empty = false;

            if (stack.getCount() != stack.getMaxStackSize()) {
                // can't be full or empty
                return HOPPER_HAS_ITEMS;
            }
        }

        return empty ? HOPPER_EMPTY : (full ? HOPPER_IS_FULL : HOPPER_HAS_ITEMS);
    }


    /**
     * @reason Combine empty / full into one check
     * @author Spottedleaf
     */
    @Overwrite
    private static boolean tryMoveItems(Level level, BlockPos blockPos, BlockState blockState, HopperBlockEntity hopperBlockEntity, BooleanSupplier booleanSupplier) {
        if (level.isClientSide) {
            return false;
        }

        if (((HopperBlockEntityMixin)(Object)hopperBlockEntity).isOnCooldown() || !blockState.getValue(HopperBlock.ENABLED).booleanValue()) {
            return false;
        }

        final int fullState = getFullState(hopperBlockEntity);

        boolean changed = false;
        if (fullState != HOPPER_EMPTY) {
            changed = ejectItems(level, blockPos, blockState, hopperBlockEntity);
        }

        // need to check changed here, as fullState will be outdated if it changed
        if (fullState != HOPPER_IS_FULL || changed) {
            changed |= booleanSupplier.getAsBoolean();
        }

        if (changed) {
            ((HopperBlockEntityMixin)(Object)hopperBlockEntity).setCooldown(HopperBlockEntity.MOVE_ITEM_SPEED);
            setChanged(level, blockPos, blockState);
            return true;
        }

        return false;
    }

    /**
     * @reason Remove streams
     * @author Spottedleaf
     */
    @Overwrite
    private static boolean isFullContainer(final Container container, final Direction direction) {
        if (container instanceof WorldlyContainer worldlyContainer) {
            for (final int slot : worldlyContainer.getSlotsForFace(direction)) {
                final ItemStack stack = container.getItem(slot);
                if (stack.getCount() < stack.getMaxStackSize()) {
                    return false;
                }
            }
            return true;
        } else {
            for (int slot = 0, max = container.getContainerSize(); slot < max; ++slot) {
                final ItemStack stack = container.getItem(slot);
                if (stack.getCount() < stack.getMaxStackSize()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * @reason Remove streams
     * @author Spottedleaf
     */
    @Overwrite
    private static boolean isEmptyContainer(final Container container, final Direction direction) {
        if (container instanceof WorldlyContainer worldlyContainer) {
            for (final int slot : worldlyContainer.getSlotsForFace(direction)) {
                final ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        } else {
            for (int slot = 0, max = container.getContainerSize(); slot < max; ++slot) {
                final ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }
}

package ca.spottedleaf.moonrise.mixin.collisions;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;
import java.util.function.Predicate;

@Mixin(ArmorStand.class)
public abstract class ArmorStandMixin extends LivingEntity {

    @Shadow
    @Final
    private static Predicate<Entity> RIDABLE_MINECARTS;

    protected ArmorStandMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * @reason Optimise this method by making it use the class lookup
     * @author Spottedleaf
     */
    @Overwrite
    public void pushEntities() {
        final List<AbstractMinecart> nearby = this.level().getEntitiesOfClass(AbstractMinecart.class, this.getBoundingBox(), RIDABLE_MINECARTS);

        for (int i = 0, len = nearby.size(); i < len; ++i) {
            final AbstractMinecart minecart = nearby.get(i);
            if (this.distanceToSqr(minecart) <= 0.2) {
                minecart.push(this);
            }
        }
    }
}

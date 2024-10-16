package ca.spottedleaf.moonrise.mixin.collisions;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;
import java.util.function.Predicate;

@Mixin(ArmorStand.class)
abstract class ArmorStandMixin extends LivingEntity {

    protected ArmorStandMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * @reason Optimise by making it use the class lookup
     * @author Spottedleaf
     */
    @Redirect(
        method = "pushEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
        )
    )
    private List<Entity> redirectGetEntities(final Level instance, final Entity entity, final AABB list, final Predicate<? super Entity> arg) {
        return (List)this.level().getEntitiesOfClass(AbstractMinecart.class, this.getBoundingBox(), arg);
    }
}

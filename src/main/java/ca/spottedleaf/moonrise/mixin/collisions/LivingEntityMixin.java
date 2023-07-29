package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.world.CollisionLevel;
import net.minecraft.world.entity.Attackable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements Attackable {

    @Shadow
    protected abstract void doPush(Entity entity);

    public LivingEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * @reason Optimise this method
     * @author Spottedleaf
     */
    @Overwrite
    public void pushEntities() {
        if (this.level().isClientSide()) {
            final List<Player> players = new ArrayList<>();
            ((CollisionLevel)this.level()).getCollisionLookup().getEntities(Player.class, this, this.getBoundingBox(), players, EntitySelector.pushableBy(this));
            for (int i = 0, len = players.size(); i < len; ++i) {
                this.doPush(players.get(i));
            }
        } else {
            final List<Entity> nearby = this.level().getEntities(this, this.getBoundingBox(), EntitySelector.pushableBy(this));

            // only iterate ONCE
            int nonPassengers = 0;
            for (int i = 0, len = nearby.size(); i < len; ++i) {
                final Entity entity = nearby.get(i);
                nonPassengers += (entity.isPassenger() ? 1 : 0);
                this.doPush(entity);
            }

            int maxCramming;
            if (nonPassengers != 0 && (maxCramming = this.level().getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING)) > 0
                && nonPassengers > (maxCramming - 1) && this.random.nextInt(4) == 0) {
                this.hurt(this.damageSources().cramming(), 6.0F);
            }
        }
    }
}

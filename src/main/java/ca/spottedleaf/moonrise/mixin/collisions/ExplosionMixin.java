package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_getblock.GetBlockChunk;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState;
import ca.spottedleaf.moonrise.patches.collisions.ExplosionBlockCache;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mixin(Explosion.class)
abstract class ExplosionMixin {

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private Entity source;

    @Shadow
    @Final
    private double x;

    @Shadow
    @Final
    private double y;

    @Shadow
    @Final
    private double z;

    @Shadow
    @Final
    private ExplosionDamageCalculator damageCalculator;

    @Shadow
    @Final
    private float radius;

    @Shadow
    @Final
    private ObjectArrayList<BlockPos> toBlow;

    @Shadow
    @Final
    private Map<Player, Vec3> hitPlayers;

    @Shadow
    @Final
    private boolean fire;

    @Shadow
    @Final
    private DamageSource damageSource;


    @Unique
    private static final double[] CACHED_RAYS;
    static {
        final DoubleArrayList rayCoords = new DoubleArrayList();

        for (int x = 0; x <= 15; ++x) {
            for (int y = 0; y <= 15; ++y) {
                for (int z = 0; z <= 15; ++z) {
                    if ((x == 0 || x == 15) || (y == 0 || y == 15) || (z == 0 || z == 15)) {
                        double xDir = (double)((float)x / 15.0F * 2.0F - 1.0F);
                        double yDir = (double)((float)y / 15.0F * 2.0F - 1.0F);
                        double zDir = (double)((float)z / 15.0F * 2.0F - 1.0F);

                        double mag = Math.sqrt(
                                xDir * xDir + yDir * yDir + zDir * zDir
                        );

                        rayCoords.add((xDir / mag) * (double)0.3F);
                        rayCoords.add((yDir / mag) * (double)0.3F);
                        rayCoords.add((zDir / mag) * (double)0.3F);
                    }
                }
            }
        }

        CACHED_RAYS = rayCoords.toDoubleArray();
    }

    @Unique
    private static final int CHUNK_CACHE_SHIFT = 2;
    @Unique
    private static final int CHUNK_CACHE_MASK = (1 << CHUNK_CACHE_SHIFT) - 1;
    @Unique
    private static final int CHUNK_CACHE_WIDTH = 1 << CHUNK_CACHE_SHIFT;

    @Unique
    private static final int BLOCK_EXPLOSION_CACHE_SHIFT = 3;
    @Unique
    private static final int BLOCK_EXPLOSION_CACHE_MASK = (1 << BLOCK_EXPLOSION_CACHE_SHIFT) - 1;
    @Unique
    private static final int BLOCK_EXPLOSION_CACHE_WIDTH = 1 << BLOCK_EXPLOSION_CACHE_SHIFT;

    // resistance = (res + 0.3F) * 0.3F;
    // so for resistance = 0, we need res = -0.3F
    @Unique
    private static final Float ZERO_RESISTANCE = Float.valueOf(-0.3f);

    @Unique
    private Long2ObjectOpenHashMap<ExplosionBlockCache> blockCache = null;

    @Unique
    private long[] chunkPosCache = null;

    @Unique
    private LevelChunk[] chunkCache = null;

    @Unique
    private ExplosionBlockCache getOrCacheExplosionBlock(final int x, final int y, final int z,
                                                         final long key, final boolean calculateResistance) {
        ExplosionBlockCache ret = this.blockCache.get(key);
        if (ret != null) {
            return ret;
        }

        BlockPos pos = new BlockPos(x, y, z);

        if (!this.level.isInWorldBounds(pos)) {
            ret = new ExplosionBlockCache(key, pos, null, null, 0.0f, true);
        } else {
            LevelChunk chunk;
            long chunkKey = CoordinateUtils.getChunkKey(x >> 4, z >> 4);
            int chunkCacheKey = ((x >> 4) & CHUNK_CACHE_MASK) | (((z >> 4) << CHUNK_CACHE_SHIFT) & (CHUNK_CACHE_MASK << CHUNK_CACHE_SHIFT));
            if (this.chunkPosCache[chunkCacheKey] == chunkKey) {
                chunk = this.chunkCache[chunkCacheKey];
            } else {
                this.chunkPosCache[chunkCacheKey] = chunkKey;
                this.chunkCache[chunkCacheKey] = chunk = this.level.getChunk(x >> 4, z >> 4);
            }

            BlockState blockState = ((GetBlockChunk)chunk).moonrise$getBlock(x, y, z);
            FluidState fluidState = blockState.getFluidState();

            Optional<Float> resistance = !calculateResistance ? Optional.empty() : this.damageCalculator.getBlockExplosionResistance((Explosion)(Object)this, this.level, pos, blockState, fluidState);

            ret = new ExplosionBlockCache(
                    key, pos, blockState, fluidState,
                    (resistance.orElse(ZERO_RESISTANCE).floatValue() + 0.3f) * 0.3f,
                    false
            );
        }

        this.blockCache.put(key, ret);

        return ret;
    }

    @Unique
    private boolean clipsAnything(final Vec3 from, final Vec3 to,
                                  final CollisionUtil.LazyEntityCollisionContext context,
                                  final ExplosionBlockCache[] blockCache,
                                  final BlockPos.MutableBlockPos currPos) {
        // assume that context.delegated = false
        final double adjX = CollisionUtil.COLLISION_EPSILON * (from.x - to.x);
        final double adjY = CollisionUtil.COLLISION_EPSILON * (from.y - to.y);
        final double adjZ = CollisionUtil.COLLISION_EPSILON * (from.z - to.z);

        if (adjX == 0.0 && adjY == 0.0 && adjZ == 0.0) {
            return false;
        }

        final double toXAdj = to.x - adjX;
        final double toYAdj = to.y - adjY;
        final double toZAdj = to.z - adjZ;
        final double fromXAdj = from.x + adjX;
        final double fromYAdj = from.y + adjY;
        final double fromZAdj = from.z + adjZ;

        int currX = Mth.floor(fromXAdj);
        int currY = Mth.floor(fromYAdj);
        int currZ = Mth.floor(fromZAdj);

        final double diffX = toXAdj - fromXAdj;
        final double diffY = toYAdj - fromYAdj;
        final double diffZ = toZAdj - fromZAdj;

        final double dxDouble = Math.signum(diffX);
        final double dyDouble = Math.signum(diffY);
        final double dzDouble = Math.signum(diffZ);

        final int dx = (int)dxDouble;
        final int dy = (int)dyDouble;
        final int dz = (int)dzDouble;

        final double normalizedDiffX = diffX == 0.0 ? Double.MAX_VALUE : dxDouble / diffX;
        final double normalizedDiffY = diffY == 0.0 ? Double.MAX_VALUE : dyDouble / diffY;
        final double normalizedDiffZ = diffZ == 0.0 ? Double.MAX_VALUE : dzDouble / diffZ;

        double normalizedCurrX = normalizedDiffX * (diffX > 0.0 ? (1.0 - Mth.frac(fromXAdj)) : Mth.frac(fromXAdj));
        double normalizedCurrY = normalizedDiffY * (diffY > 0.0 ? (1.0 - Mth.frac(fromYAdj)) : Mth.frac(fromYAdj));
        double normalizedCurrZ = normalizedDiffZ * (diffZ > 0.0 ? (1.0 - Mth.frac(fromZAdj)) : Mth.frac(fromZAdj));

        for (;;) {
            currPos.set(currX, currY, currZ);

            // ClipContext.Block.COLLIDER -> BlockBehaviour.BlockStateBase::getCollisionShape
            // ClipContext.Fluid.NONE -> ignore fluids

            // read block from cache
            final long key = BlockPos.asLong(currX, currY, currZ);

            final int cacheKey =
                    (currX & BLOCK_EXPLOSION_CACHE_MASK) |
                    (currY & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT) |
                    (currZ & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT + BLOCK_EXPLOSION_CACHE_SHIFT);
            ExplosionBlockCache cachedBlock = blockCache[cacheKey];
            if (cachedBlock == null || cachedBlock.key != key) {
                blockCache[cacheKey] = cachedBlock = this.getOrCacheExplosionBlock(currX, currY, currZ, key, false);
            }

            final BlockState blockState = cachedBlock.blockState;
            if (blockState != null && !((CollisionBlockState)blockState).moonrise$emptyContextCollisionShape()) {
                VoxelShape collision = cachedBlock.cachedCollisionShape;
                if (collision == null) {
                    collision = ((CollisionBlockState)blockState).moonrise$getConstantContextCollisionShape();
                    if (collision == null) {
                        collision = blockState.getCollisionShape(this.level, currPos, context);
                        if (!context.isDelegated()) {
                            // if it was not delegated during this call, assume that for any future ones it will not be delegated
                            // again, and cache the result
                            cachedBlock.cachedCollisionShape = collision;
                        }
                    } else {
                        cachedBlock.cachedCollisionShape = collision;
                    }
                }

                if (!collision.isEmpty() && collision.clip(from, to, currPos) != null) {
                    return true;
                }
            }

            if (normalizedCurrX > 1.0 && normalizedCurrY > 1.0 && normalizedCurrZ > 1.0) {
                return false;
            }

            // inc the smallest normalized coordinate

            if (normalizedCurrX < normalizedCurrY) {
                if (normalizedCurrX < normalizedCurrZ) {
                    currX += dx;
                    normalizedCurrX += normalizedDiffX;
                } else {
                    // x < y && x >= z <--> z < y && z <= x
                    currZ += dz;
                    normalizedCurrZ += normalizedDiffZ;
                }
            } else if (normalizedCurrY < normalizedCurrZ) {
                // y <= x && y < z
                currY += dy;
                normalizedCurrY += normalizedDiffY;
            } else {
                // y <= x && z <= y <--> z <= y && z <= x
                currZ += dz;
                normalizedCurrZ += normalizedDiffZ;
            }
        }
    }

    @Unique
    private float getSeenFraction(final Vec3 source, final Entity target,
                                   final ExplosionBlockCache[] blockCache,
                                   final BlockPos.MutableBlockPos blockPos) {
        final AABB boundingBox = target.getBoundingBox();
        final double diffX = boundingBox.maxX - boundingBox.minX;
        final double diffY = boundingBox.maxY - boundingBox.minY;
        final double diffZ = boundingBox.maxZ - boundingBox.minZ;

        final double incX = 1.0 / (diffX * 2.0 + 1.0);
        final double incY = 1.0 / (diffY * 2.0 + 1.0);
        final double incZ = 1.0 / (diffZ * 2.0 + 1.0);

        if (incX < 0.0 || incY < 0.0 || incZ < 0.0) {
            return 0.0f;
        }

        final double offX = (1.0 - Math.floor(1.0 / incX) * incX) * 0.5 + boundingBox.minX;
        final double offY = boundingBox.minY;
        final double offZ = (1.0 - Math.floor(1.0 / incZ) * incZ) * 0.5 + boundingBox.minZ;

        final CollisionUtil.LazyEntityCollisionContext context = new CollisionUtil.LazyEntityCollisionContext(target);

        int totalRays = 0;
        int missedRays = 0;

        for (double dx = 0.0; dx <= 1.0; dx += incX) {
            final double fromX = Math.fma(dx, diffX, offX);
            for (double dy = 0.0; dy <= 1.0; dy += incY) {
                final double fromY = Math.fma(dy, diffY, offY);
                for (double dz = 0.0; dz <= 1.0; dz += incZ) {
                    ++totalRays;

                    final Vec3 from = new Vec3(
                            fromX,
                            fromY,
                            Math.fma(dz, diffZ, offZ)
                    );

                    if (!this.clipsAnything(from, source, context, blockCache, blockPos)) {
                        ++missedRays;
                    }
                }
            }
        }

        return (float)missedRays / (float)totalRays;
    }

    /**
     * @reason Rewrite ray casting and seen fraction calculation for performance
     * @author Spottedleaf
     */
    @Overwrite
    public void explode() {
        this.level.gameEvent(this.source, GameEvent.EXPLODE, new Vec3(this.x, this.y, this.z));

        this.blockCache = new Long2ObjectOpenHashMap<>();

        this.chunkPosCache = new long[CHUNK_CACHE_WIDTH * CHUNK_CACHE_WIDTH];
        Arrays.fill(this.chunkPosCache, ChunkPos.INVALID_CHUNK_POS);

        this.chunkCache = new LevelChunk[CHUNK_CACHE_WIDTH * CHUNK_CACHE_WIDTH];

        final ExplosionBlockCache[] blockCache = new ExplosionBlockCache[BLOCK_EXPLOSION_CACHE_WIDTH * BLOCK_EXPLOSION_CACHE_WIDTH * BLOCK_EXPLOSION_CACHE_WIDTH];

        // use initial cache value that is most likely to be used: the source position
        final ExplosionBlockCache initialCache;
        {
            final int blockX = Mth.floor(this.x);
            final int blockY = Mth.floor(this.y);
            final int blockZ = Mth.floor(this.z);

            final long key = BlockPos.asLong(blockX, blockY, blockZ);

            initialCache = this.getOrCacheExplosionBlock(blockX, blockY, blockZ, key, true);
        }

        // only ~1/3rd of the loop iterations in vanilla will result in a ray, as it is iterating the perimeter of
        // a 16x16x16 cube
        // we can cache the rays and their normals as well, so that we eliminate the excess iterations / checks and
        // calculations in one go
        // additional aggressive caching of block retrieval is very significant, as at low power (i.e tnt) most
        // block retrievals are not unique
        for (int ray = 0, len = CACHED_RAYS.length; ray < len;) {
            ExplosionBlockCache cachedBlock = initialCache;

            double currX = this.x;
            double currY = this.y;
            double currZ = this.z;

            final double incX = CACHED_RAYS[ray];
            final double incY = CACHED_RAYS[ray + 1];
            final double incZ = CACHED_RAYS[ray + 2];

            ray += 3;

            float power = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);

            do {
                final int blockX = Mth.floor(currX);
                final int blockY = Mth.floor(currY);
                final int blockZ = Mth.floor(currZ);

                final long key = BlockPos.asLong(blockX, blockY, blockZ);

                if (cachedBlock.key != key) {
                    final int cacheKey =
                            (blockX & BLOCK_EXPLOSION_CACHE_MASK) |
                            (blockY & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT) |
                            (blockZ & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT + BLOCK_EXPLOSION_CACHE_SHIFT);
                    cachedBlock = blockCache[cacheKey];
                    if (cachedBlock == null || cachedBlock.key != key) {
                        blockCache[cacheKey] = cachedBlock = this.getOrCacheExplosionBlock(blockX, blockY, blockZ, key, true);
                    }
                }

                if (cachedBlock.outOfWorld) {
                    break;
                }

                power -= cachedBlock.resistance;

                if (power > 0.0f && cachedBlock.shouldExplode == null) {
                    // note: we expect shouldBlockExplode to be pure with respect to power, as Vanilla currently is.
                    // basically, it is unused, which allows us to cache the result
                    final boolean shouldExplode = this.damageCalculator.shouldBlockExplode((Explosion)(Object)this, this.level, cachedBlock.immutablePos, cachedBlock.blockState, power);
                    cachedBlock.shouldExplode = shouldExplode ? Boolean.TRUE : Boolean.FALSE;
                    if (shouldExplode) {
                        if (this.fire || !cachedBlock.blockState.isAir()) {
                            this.toBlow.add(cachedBlock.immutablePos);
                        }
                    }
                }

                power -= 0.22500001F;
                currX += incX;
                currY += incY;
                currZ += incZ;
            } while (power > 0.0f);
        }

        final double diameter = (double)this.radius * 2.0;
        final List<Entity> entities = this.level.getEntities(this.source,
                new AABB(
                        (double)Mth.floor(this.x - (diameter + 1.0)),
                        (double)Mth.floor(this.y - (diameter + 1.0)),
                        (double)Mth.floor(this.z - (diameter + 1.0)),

                        (double)Mth.floor(this.x + (diameter + 1.0)),
                        (double)Mth.floor(this.y + (diameter + 1.0)),
                        (double)Mth.floor(this.z + (diameter + 1.0))
                )
        );
        final Vec3 center = new Vec3(this.x, this.y, this.z);

        final BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        final PlatformHooks platformHooks = PlatformHooks.get();

        platformHooks.onExplosion(this.level, (Explosion)(Object)this, entities, diameter);
        for (int i = 0, len = entities.size(); i < len; ++i) {
            final Entity entity = entities.get(i);
            if (entity.ignoreExplosion((Explosion)(Object)this)) {
                continue;
            }

            final double normalizedDistanceToCenter = Math.sqrt(entity.distanceToSqr(center)) / diameter;
            if (normalizedDistanceToCenter > 1.0) {
                continue;
            }

            double distX = entity.getX() - this.x;
            double distY = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.y;
            double distZ = entity.getZ() - this.z;
            final double distMag = Math.sqrt(distX * distX + distY * distY + distZ * distZ);

            if (distMag == 0.0) {
                continue;
            }

            distX /= distMag;
            distY /= distMag;
            distZ /= distMag;

            // route to new visible fraction calculation, using the existing block cache
            final double seenFraction = (double)this.getSeenFraction(center, entity, blockCache, blockPos);
            if (this.damageCalculator.shouldDamageEntity((Explosion)(Object)this, entity)) {
                // inline getEntityDamageAmount so that we can avoid double calling getSeenPercent, which is the MOST
                // expensive part of this loop!!!!
                final double factor = (1.0 - normalizedDistanceToCenter) * seenFraction;
                entity.hurt(this.damageSource, (float)((factor * factor + factor) / 2.0 * 7.0 * diameter + 1.0));
            }

            final double intensityFraction = (1.0 - normalizedDistanceToCenter) * seenFraction * (double)this.damageCalculator.getKnockbackMultiplier(entity);


            final double knockbackFraction;
            if (entity instanceof LivingEntity livingEntity) {
                knockbackFraction = intensityFraction * (1.0 - livingEntity.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE));
            } else {
                knockbackFraction = intensityFraction;
            }

            Vec3 knockback = new Vec3(distX * knockbackFraction, distY * knockbackFraction, distZ * knockbackFraction);
            knockback = platformHooks.modifyExplosionKnockback(this.level, (Explosion)(Object)this, entity, knockback);
            entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

            if (entity instanceof Player player) {
                if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
                    this.hitPlayers.put(player, knockback);
                }
            }

            entity.onExplosionHit(this.source);
        }

        this.blockCache = null;
        this.chunkPosCache = null;
        this.chunkCache = null;
    }
}

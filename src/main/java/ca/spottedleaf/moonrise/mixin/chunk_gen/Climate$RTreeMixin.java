package ca.spottedleaf.moonrise.mixin.chunk_gen;

import ca.spottedleaf.moonrise.patches.chunk_gen.ChunkGenRTree;
import ca.spottedleaf.moonrise.patches.chunk_gen.RTreeLeafDistance;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Climate.RTree.class)
abstract class Climate$RTreeMixin<T> implements ChunkGenRTree<T> {

    @Shadow
    @Final
    private Climate.RTree.Node<T> root;

    @Unique
    private final ThreadLocal<RTreeLeafDistance<T>> lastReturned = ThreadLocal.withInitial(() -> {
        return new RTreeLeafDistance<>(null, Long.MAX_VALUE);
    });

    @Unique
    private void findNearest0(final long[] params, final RTreeLeafDistance<T> suggested, final Climate.RTree.SubTree<T> node) {
        for (final Climate.RTree.Node<T> child : node.children) {
            final long childDist = child.distance(params);
            if (childDist >= suggested.distance) {
                continue;
            }
            if (child instanceof Climate.RTree.Leaf<T> leaf) {
                suggested.leaf = leaf;
                suggested.distance = childDist;
                continue;
            } else {
                this.findNearest0(params, suggested, (Climate.RTree.SubTree<T>)child);
                continue;
            }
        }
    }

    @Override
    public final T moonrise$findNearest(final Climate.TargetPoint target) {
        if (this.root == null) {
            return null;
        }

        if (this.root instanceof Climate.RTree.Leaf<T> ret) {
            return ret.value;
        }

        final long[] params = target.toParameterArray();

        final RTreeLeafDistance<T> ret = this.lastReturned.get();
        if (ret.leaf != null) {
            ret.distance = ret.leaf.distance(params);
        }

        this.findNearest0(params, ret, ((Climate.RTree.SubTree<T>)this.root));

        return ret.leaf.value;
    }
}

package ca.spottedleaf.moonrise.mixin.chunk_gen.list_itr;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.AllOfRuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(AllOfRuleTest.class)
abstract class AllOfRuleTestMixin extends RuleTest {

    @Unique
    private RuleTest[] rulesArray;

    /**
     * @reason Use array for iteration
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void initArr(final List<RuleTest> rules, final CallbackInfo ci) {
        this.rulesArray = rules.toArray(new RuleTest[0]);
    }

    /**
     * @reason Use array for iteration
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public boolean test(final BlockState blockState, final BlockPos pos, final RandomSource random) {
        for (final RuleTest rule : this.rulesArray) {
            if (!rule.test(blockState, pos, random)) {
                return false;
            }
        }

        return true;
    }
}

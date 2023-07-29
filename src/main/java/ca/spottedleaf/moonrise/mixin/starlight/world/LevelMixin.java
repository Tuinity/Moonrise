package ca.spottedleaf.moonrise.mixin.starlight.world;

import ca.spottedleaf.moonrise.patches.starlight.world.StarlightWorld;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelAccessor, AutoCloseable, StarlightWorld {}

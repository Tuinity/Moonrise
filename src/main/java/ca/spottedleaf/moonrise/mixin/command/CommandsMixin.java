package ca.spottedleaf.moonrise.mixin.command;

import ca.spottedleaf.moonrise.patches.command.MoonriseCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
abstract class CommandsMixin {

    /**
     * @reason Hook for registering the moonrise command
     * @author Spottedleaf
     */
    @Inject(
            method = "registerVanillaCommands",
            at = @At(
                    value = "RETURN"
            )
    )
    private static void registerCommands(final CommandDispatcher<CommandSourceStack> dispatcher, final Commands.CommandSelection commandSelection,
                                         final CommandBuildContext commandBuildContext, final CallbackInfo ci) {
        MoonriseCommand.register(dispatcher);
    }
}

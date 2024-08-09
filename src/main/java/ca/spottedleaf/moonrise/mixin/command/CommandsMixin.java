package ca.spottedleaf.moonrise.mixin.command;

import ca.spottedleaf.moonrise.patches.command.MoonriseCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
abstract class CommandsMixin {

    @Shadow
    @Final
    private CommandDispatcher<CommandSourceStack> dispatcher;

    /**
     * @reason Hook for registering the moonrise command
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/CommandDispatcher;setConsumer(Lcom/mojang/brigadier/ResultConsumer;)V"
            )
    )
    private void registerCommands(final Commands.CommandSelection commandSelection, final CommandBuildContext commandBuildContext, final CallbackInfo ci) {
        MoonriseCommand.register(this.dispatcher);
        if (commandSelection == Commands.CommandSelection.INTEGRATED) {
            MoonriseCommand.registerClient(this.dispatcher);
        }
    }
}

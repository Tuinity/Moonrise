package ca.spottedleaf.moonrise.neoforge.mixin.command;

import ca.spottedleaf.moonrise.patches.command.CommandClientCommandSource;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientCommandSourceStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientCommandSourceStack.class)
abstract class ClientCommandSourceStackMixin extends CommandSourceStack implements CommandClientCommandSource {
    public ClientCommandSourceStackMixin(CommandSource arg, Vec3 arg2, Vec2 arg3, ServerLevel arg4, int i, String string, Component arg5, MinecraftServer minecraftServer, @Nullable Entity arg6) {
        super(arg, arg2, arg3, arg4, i, string, arg5, minecraftServer, arg6);
    }

    @Shadow
    public abstract void sendSuccess(Supplier<Component> message, boolean sendToAdmins);

    @Override
    public void moonrise$sendFailure(final Component message) {
        this.sendFailure(message);
    }

    @Override
    public void moonrise$sendSuccess(final Component message) {
        this.sendSuccess(() -> message, true);
    }
}

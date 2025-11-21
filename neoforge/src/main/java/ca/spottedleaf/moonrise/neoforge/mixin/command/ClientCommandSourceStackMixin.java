package ca.spottedleaf.moonrise.neoforge.mixin.command;

import ca.spottedleaf.moonrise.patches.command.CommandClientCommandSource;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientCommandSourceStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientCommandSourceStack.class)
abstract class ClientCommandSourceStackMixin extends CommandSourceStack implements CommandClientCommandSource {
    public ClientCommandSourceStackMixin(final CommandSource source, final Vec3 worldPosition, final Vec2 rotation, final ServerLevel level, final PermissionSet permissions, final String textName, final Component displayName, final MinecraftServer server, @Nullable final Entity entity) {
        super(source, worldPosition, rotation, level, permissions, textName, displayName, server, entity);
    }

    @Shadow
    public abstract void sendSuccess(Supplier<Component> message, boolean sendToAdmins);

    @Override
    public final void moonrise$sendFailure(final Component message) {
        this.sendFailure(message);
    }

    @Override
    public final void moonrise$sendSuccess(final Component message) {
        this.sendSuccess(() -> message, true);
    }
}

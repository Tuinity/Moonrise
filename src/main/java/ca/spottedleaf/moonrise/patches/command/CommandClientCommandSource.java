package ca.spottedleaf.moonrise.patches.command;

import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public interface CommandClientCommandSource extends SharedSuggestionProvider {
	void moonrise$sendSuccess(Component message);

	void moonrise$sendFailure(Component message);
}

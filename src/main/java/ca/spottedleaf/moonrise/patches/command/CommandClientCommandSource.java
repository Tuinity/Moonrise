package ca.spottedleaf.moonrise.patches.command;

import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.network.chat.Component;

public interface CommandClientCommandSource extends SuggestionContext {
	void moonrise$sendSuccess(Component message);

	void moonrise$sendFailure(Component message);
}

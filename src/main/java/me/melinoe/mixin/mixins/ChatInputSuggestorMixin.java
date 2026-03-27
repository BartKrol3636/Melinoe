package me.melinoe.mixin.mixins;

import com.mojang.brigadier.suggestion.Suggestions;
import me.melinoe.utils.emoji.EmojiSuggestionProvider;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class ChatInputSuggestorMixin {

    @Shadow private EditBox input;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Inject(method = "updateCommandInfo", at = @At("RETURN"))
    private void melinoe$setEmojiSuggestions(CallbackInfo ci) {
        if (this.input == null) return;

        String text = this.input.getValue();
        if (text == null || text.isEmpty() || text.startsWith("/")) return;

        if (!EmojiSuggestionProvider.INSTANCE.isTypingEmoji(this.input)) return;

        CompletableFuture<Suggestions> emojiSuggestionsFuture = EmojiSuggestionProvider.INSTANCE.provideSuggestions(
                text, this.input.getCursorPosition()
        );

        // Dynamically merge the mod's suggestions with the server's live suggestion packet
        if (this.pendingSuggestions != null) {
            this.pendingSuggestions = this.pendingSuggestions.thenCombine(emojiSuggestionsFuture, (serverSugs, modSugs) ->
                    EmojiSuggestionProvider.INSTANCE.mergeAndCheckPerks(serverSugs, modSugs)
            );
        } else {
            this.pendingSuggestions = emojiSuggestionsFuture;
        }

        this.showSuggestions(false);
    }
}
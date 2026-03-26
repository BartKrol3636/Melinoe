package me.melinoe.mixin.mixins;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import me.melinoe.features.impl.misc.ChatEmojisModule;
import me.melinoe.utils.EmojiSuggestionProvider;
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
        if (!ChatEmojisModule.INSTANCE.getEnabled()) {
            return;
        }
        
        if (input == null) {
            return;
        }
        
        String text = input.getValue();
        if (text == null) {
            return;
        }
        
        if (!EmojiSuggestionProvider.isTypingEmoji(input)) {
            return;
        }
        
        CompletableFuture<Suggestions> emojiSuggestions = EmojiSuggestionProvider.provideSuggestions(
            text, input.getCursorPosition()
        );
        
        Suggestions emojiSugs = emojiSuggestions.join();
        if (emojiSugs.getList().isEmpty()) {
            return;
        }
        
        if (text.startsWith("/") && pendingSuggestions != null && pendingSuggestions.isDone()) {
            try {
                Suggestions existing = pendingSuggestions.getNow(null);
                if (existing != null && !existing.getList().isEmpty()) {
                    java.util.List<Suggestion> merged = new java.util.ArrayList<>(existing.getList());
                    merged.addAll(emojiSugs.getList());
                    pendingSuggestions = CompletableFuture.completedFuture(
                        new Suggestions(emojiSugs.getRange(), merged)
                    );
                    return;
                }
            } catch (Exception e) {
                // If merge fails, just use emoji suggestions
            }
        }
        
        pendingSuggestions = CompletableFuture.completedFuture(emojiSugs);
        this.showSuggestions(false);
    }
}

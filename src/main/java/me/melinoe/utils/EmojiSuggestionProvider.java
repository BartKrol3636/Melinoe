package me.melinoe.utils;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Provides emoji autocomplete suggestions for chat input.
 */
public class EmojiSuggestionProvider {
    
    public static boolean isTypingEmoji(EditBox field) {
        if (field == null) return false;
        
        String text = field.getValue();
        if (text == null || text.isEmpty()) return false;
        
        int cursor = field.getCursorPosition();
        if (cursor == 0) return false;
        
        int lastColon = text.lastIndexOf(':', cursor - 1);
        if (lastColon < 0) return false;
        
        if (cursor - lastColon < 2) return false;
        
        String token = text.substring(lastColon, cursor);
        
        return token.matches(":[a-z0-9_]+:?");
    }
    
    public static CompletableFuture<Suggestions> provideSuggestions(String input, int cursor) {
        if (input == null || cursor < 0 || cursor > input.length()) {
            return Suggestions.empty();
        }
        
        int lastColon = input.lastIndexOf(':', cursor - 1);
        if (lastColon < 0) {
            return Suggestions.empty();
        }
        
        String token = input.substring(lastColon, cursor);
        if (token.length() < 2) {
            return Suggestions.empty();
        }
        
        String searchText = token.substring(1).toLowerCase();
        if (searchText.endsWith(":")) {
            searchText = searchText.substring(0, searchText.length() - 1);
        }
        
        if (searchText.isEmpty()) {
            return Suggestions.empty();
        }
        
        List<Suggestion> suggestions = new ArrayList<>();
        Map<String, String> mappings = EmojiShortcodes.getMappings();
        
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            if (suggestions.size() >= 10) break;
            
            String shortcode = entry.getKey();
            String emoji = entry.getValue();
            
            String shortcodeBase = shortcode.replace(":", "").toLowerCase();
            
            if (shortcodeBase.startsWith(searchText)) {
                String displayText = shortcode + " " + emoji;
                
                suggestions.add(new Suggestion(
                    StringRange.between(lastColon, cursor),
                    shortcode,
                    Component.literal(displayText)
                ));
            }
        }
        
        if (suggestions.isEmpty()) {
            return Suggestions.empty();
        }
        
        return CompletableFuture.completedFuture(
            new Suggestions(StringRange.between(lastColon, cursor), suggestions)
        );
    }
}

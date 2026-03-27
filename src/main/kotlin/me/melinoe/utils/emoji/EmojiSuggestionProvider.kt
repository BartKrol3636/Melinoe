package me.melinoe.utils.emoji

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestion
import com.mojang.brigadier.suggestion.Suggestions
import net.minecraft.client.gui.components.EditBox
import java.util.concurrent.CompletableFuture

object EmojiSuggestionProvider {
    
    private fun findOpeningColon(text: String, cursor: Int): Int {
        var start = cursor - 1
        var endsWithColon = false
        if (start >= 0 && text[start] == ':') { endsWithColon = true; start-- }
        while (start >= 0) {
            val c = text[start]
            if (c == ':') return start
            if (c == ' ') return if (endsWithColon) cursor - 1 else -1
            start--
        }
        return if (start < 0 && endsWithColon) cursor - 1 else -1
    }
    
    fun isTypingEmoji(field: EditBox?): Boolean {
        if (field == null) return false
        val text = field.value
        if (text.isEmpty() || text.startsWith("/")) return false
        
        val cursor = field.cursorPosition
        if (cursor <= 0 || cursor > text.length) return false
        
        val start = findOpeningColon(text, cursor)
        if (start < 0) return false
        
        return text.substring(start, cursor).matches(Regex("(?i):[a-z0-9_]*:?"))
    }
    
    fun provideSuggestions(input: String?, cursor: Int): CompletableFuture<Suggestions> {
        if (input.isNullOrEmpty() || cursor <= 0 || cursor > input.length) return Suggestions.empty()
        
        val start = findOpeningColon(input, cursor)
        if (start < 0) return Suggestions.empty()
        
        val token = input.substring(start, cursor)
        var searchText = ""
        
        if (token.length > 1) {
            searchText = token.substring(1).lowercase()
            if (searchText.endsWith(":")) searchText = searchText.dropLast(1)
        }
        
        val allValidSuggestions = mutableListOf<Suggestion>()
        
        for (data in EmojiShortcodes.suggestionList) {
            // Wait for the async server packet in `mergeAndCheckPerks` to unlock this!
            if (data.isServerEmoji && !EmojiShortcodes.hasSupporterPerks) continue
            
            if (searchText.isEmpty() || data.cleanName.contains(searchText)) {
                allValidSuggestions.add(
                    Suggestion(StringRange.between(start, cursor), data.suggestionString)
                )
            }
        }
        
        allValidSuggestions.sortWith(compareBy(
            { suggestion ->
                val rawSpace = suggestion.text.indexOf(' ')
                val cleanedName = suggestion.text.substringBefore("*").substring(1, if (rawSpace > 0) rawSpace - 1 else suggestion.text.length).lowercase()
                when {
                    cleanedName == searchText -> 0
                    cleanedName.startsWith(searchText) -> 1
                    else -> 2
                }
            },
            { it.text }
        ))
        
        if (allValidSuggestions.isEmpty()) return Suggestions.empty()
        
        return CompletableFuture.completedFuture(
            Suggestions(StringRange.between(start, cursor), allValidSuggestions.take(200))
        )
    }
    
    fun mergeAndCheckPerks(serverSugs: Suggestions, modSugs: Suggestions): Suggestions {
        if (!EmojiShortcodes.hasSupporterPerks) {
            // If the server's suggestion packet contains ANY of our server emojis, the user has the perk.
            if (serverSugs.list.any { EmojiShortcodes.serverEmojis.containsKey(it.text) }) {
                EmojiShortcodes.hasSupporterPerks = true
            }
        }
        
        val finalModSugs = if (!EmojiShortcodes.hasSupporterPerks) {
            modSugs.list.filter { !it.text.contains("*") }
        } else {
            modSugs.list
        }
        
        val ourShortcodes = finalModSugs.map { it.text.substringBefore("*").substringBefore(" ") }.toSet()
        val merged = finalModSugs.toMutableList()
        
        for (s in serverSugs.list) {
            val cleanText = s.text
            // Filter out the ugly server ones if we are rendering the pretty mod versions
            if (!ourShortcodes.contains(cleanText) && !ourShortcodes.contains("$cleanText:")) {
                merged.add(s)
            }
        }
        
        return Suggestions(modSugs.range, merged)
    }
}
package me.melinoe.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads shortcode -> Unicode emoji mappings from resources.
 */
public final class EmojiShortcodes {
    private static Map<String, String> cache;
    private static Map<String, String> reverseCache;

    private EmojiShortcodes() {}

    public static synchronized Map<String, String> getMappings() {
        if (cache != null) return cache;

        Map<String, String> map = new HashMap<>();

        // Load mappings from resource file
        try (InputStream is = EmojiShortcodes.class.getClassLoader()
                .getResourceAsStream("assets/melinoe/emoji/shortcodes.properties")) {
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                        int eq = trimmed.indexOf('=');
                        if (eq <= 0) continue;
                        String key = trimmed.substring(0, eq).trim();
                        String value = trimmed.substring(eq + 1).trim();
                        if (!key.isEmpty() && !value.isEmpty()) {
                            map.put(key, value);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        cache = Collections.unmodifiableMap(map);
        return cache;
    }

    public static synchronized Map<String, String> getReverseMappings() {
        if (reverseCache != null) return reverseCache;
        Map<String, String> rev = new HashMap<>();
        for (Map.Entry<String, String> e : getMappings().entrySet()) {
            rev.putIfAbsent(e.getValue(), e.getKey());
        }
        reverseCache = Collections.unmodifiableMap(rev);
        return reverseCache;
    }

    /**
     * Replace emoji Unicode with shortcodes.
     */
    public static String replaceEmojiWithShortcodes(String input) {
        if (input == null || input.isEmpty()) return input;
        
        if (!containsEmoji(input)) return input;
        
        Map<String, String> reverseMap = getReverseMappings();
        StringBuilder sb = null;
        int lastPos = 0;
        int len = input.length();
        
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            
            if (!isEmojiStart(c)) continue;
            
            String bestMatch = null;
            String bestShortcode = null;
            
            for (int endPos = Math.min(i + 4, len); endPos > i; endPos--) {
                String candidate = input.substring(i, endPos);
                String shortcode = reverseMap.get(candidate);
                if (shortcode != null) {
                    bestMatch = candidate;
                    bestShortcode = shortcode;
                    break;
                }
            }
            
            if (bestMatch != null) {
                if (sb == null) {
                    sb = new StringBuilder(input.length());
                }
                
                sb.append(input, lastPos, i);
                sb.append(bestShortcode);
                i += bestMatch.length() - 1;
                lastPos = i + 1;
            }
        }
        
        if (sb == null) return input;
        
        sb.append(input, lastPos, len);
        return sb.toString();
    }
    
    private static boolean containsEmoji(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (isEmojiStart(s.charAt(i))) return true;
        }
        return false;
    }
    
    private static boolean isEmojiStart(char c) {
        if (Character.isHighSurrogate(c)) return true;
        return c >= 0x200D || c == 0x2764 || c == 0xFE0F || 
               (c >= 0x1F300 && c <= 0x1F9FF);
    }

    /**
     * Replace shortcodes like :skull: with emoji Unicode like 💀
     */
    public static String replaceShortcodesWithEmojis(String input) {
        if (input == null || input.isEmpty() || input.indexOf(':') == -1) return input;
        
        Map<String, String> mappings = getMappings();
        StringBuilder sb = null;
        int lastPos = 0;
        int len = input.length();
        
        for (int i = 0; i < len; i++) {
            if (input.charAt(i) != ':') continue;
            
            int maxSearch = Math.min(i + 21, len);
            int closingColon = -1;
            
            for (int j = i + 2; j < maxSearch; j++) {
                if (input.charAt(j) == ':') {
                    closingColon = j;
                    break;
                }
            }
            
            if (closingColon == -1) continue;
            
            String candidate = input.substring(i, closingColon + 1);
            String emoji = mappings.get(candidate);
            
            if (emoji != null) {
                if (sb == null) {
                    sb = new StringBuilder(input.length());
                }
                
                sb.append(input, lastPos, i);
                sb.append(emoji);
                i = closingColon;
                lastPos = i + 1;
            }
        }
        
        if (sb == null) return input;
        
        sb.append(input, lastPos, len);
        return sb.toString();
    }
}

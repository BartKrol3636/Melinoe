package me.melinoe.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Collections;
import java.util.Map;

/**
 * Replaces emoji shortcodes like :sob: with Unicode emoji characters.
 */
public final class EmojiReplacer {
    private static final Map<String, String> SHORTCODE_TO_GLYPH;

    static {
        SHORTCODE_TO_GLYPH = Collections.unmodifiableMap(EmojiShortcodes.getMappings());
    }

    private EmojiReplacer() {}

    public static Component replaceIn(Component input) {
        return replaceTree(input);
    }

    public static String replaceInString(String s) {
        if (s == null || s.isEmpty() || s.indexOf(':') == -1) {
            return s;
        }
        
        StringBuilder sb = null;
        int lastPos = 0;
        int len = s.length();
        
        for (int i = 0; i < len; i++) {
            if (s.charAt(i) != ':') continue;
            
            int maxSearch = Math.min(i + 21, len);
            int closingColon = -1;
            
            for (int j = i + 2; j < maxSearch; j++) {
                if (s.charAt(j) == ':') {
                    closingColon = j;
                    break;
                }
            }
            
            if (closingColon == -1) continue;
            
            String candidate = s.substring(i, closingColon + 1);
            String emoji = SHORTCODE_TO_GLYPH.get(candidate);
            
            if (emoji != null) {
                if (sb == null) {
                    sb = new StringBuilder(s.length());
                }
                
                sb.append(s, lastPos, i);
                sb.append(emoji);
                i = closingColon;
                lastPos = i + 1;
            }
        }
        
        if (sb == null) return s;
        
        sb.append(s, lastPos, len);
        return sb.toString();
    }

    /**
     * Recursively replace emojis in Component tree, preserving structure and styles.
     */
    private static Component replaceTree(Component node) {
        MutableComponent result;
        
        if (node.getContents() instanceof PlainTextContents.LiteralContents literal) {
            String original = literal.text();
            String replaced = replaceInString(original);
            
            // If replacement occurred, split into segments with proper coloring
            if (!replaced.equals(original)) {
                result = replaceWithColorPreservation(original, replaced, node.getStyle());
            } else {
                result = Component.literal(replaced).setStyle(node.getStyle());
            }
        } else if (node.getContents() instanceof TranslatableContents tc) {
            // Handle translatable components by replacing in arguments
            Object[] args = tc.getArgs();
            Object[] replacedArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a instanceof Component t) {
                    replacedArgs[i] = replaceTree(t);
                } else if (a instanceof String s) {
                    replacedArgs[i] = replaceInString(s);
                } else {
                    replacedArgs[i] = a;
                }
            }
            result = Component.translatable(tc.getKey(), replacedArgs).setStyle(node.getStyle());
        } else {
            result = node.copy();
            result.getSiblings().clear();
        }

        for (Component sibling : node.getSiblings()) {
            result.append(replaceTree(sibling));
        }
        
        return result;
    }
    
    /**
     * Replace shortcodes while preserving colors.
     * Emojis get white color, text keeps original style.
     */
    private static MutableComponent replaceWithColorPreservation(String original, String replaced, net.minecraft.network.chat.Style baseStyle) {
        MutableComponent result = Component.empty();
        
        StringBuilder currentText = new StringBuilder();
        int pos = 0;
        int len = original.length();
        
        while (pos < len) {
            if (original.charAt(pos) != ':') {
                currentText.append(original.charAt(pos));
                pos++;
                continue;
            }
            
            // Find the closing colon (limit search to reasonable shortcode length)
            int maxSearch = Math.min(pos + 21, len); // Max shortcode is ~20 chars
            int closingColon = -1;
            
            for (int j = pos + 2; j < maxSearch; j++) { // Min shortcode: :x:
                if (original.charAt(j) == ':') {
                    closingColon = j;
                    break;
                }
            }
            
            if (closingColon == -1) {
                currentText.append(original.charAt(pos));
                pos++;
                continue;
            }
            
            String candidate = original.substring(pos, closingColon + 1);
            String emoji = SHORTCODE_TO_GLYPH.get(candidate);
            
            if (emoji != null) {
                if (currentText.length() > 0) {
                    result.append(Component.literal(currentText.toString()).setStyle(baseStyle));
                    currentText.setLength(0);
                }
                
                result.append(Component.literal(emoji).withStyle(ChatFormatting.WHITE));
                pos = closingColon + 1;
            } else {
                currentText.append(original.charAt(pos));
                pos++;
            }
        }
        
        if (currentText.length() > 0) {
            result.append(Component.literal(currentText.toString()).setStyle(baseStyle));
        }
        
        return result;
    }
}

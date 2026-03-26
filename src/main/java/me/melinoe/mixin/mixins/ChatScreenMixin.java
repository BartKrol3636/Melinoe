package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.misc.ChatEmojisModule;
import me.melinoe.utils.EmojiShortcodes;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Converts emoji Unicode back to shortcodes before sending to telos.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    
    @ModifyVariable(
        method = "handleChatInput",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private String melinoe$convertEmojisToShortcodes(String message) {
        if (!ChatEmojisModule.INSTANCE.getEnabled()) {
            return message;
        }
        
        return EmojiShortcodes.replaceEmojiWithShortcodes(message);
    }
}

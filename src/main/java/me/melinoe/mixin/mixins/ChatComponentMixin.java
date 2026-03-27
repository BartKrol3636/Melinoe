package me.melinoe.mixin.mixins;

import me.melinoe.utils.emoji.EmojiReplacer;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Ensures incoming server messages containing `:shortcodes:` are properly
 * converted and rendered as their unicode counterparts in the chat history.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component melinoe$replaceEmojisInChat(Component message) {
        return EmojiReplacer.INSTANCE.replaceIn(message);
    }
}
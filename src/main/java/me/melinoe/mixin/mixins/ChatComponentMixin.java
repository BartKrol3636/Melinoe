package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.misc.ChatModule;
import me.melinoe.utils.emoji.EmojiReplacer;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Filters incoming text based on tabs, and catches chat mode switches
 * Ensures incoming server messages containing `:shortcodes:` are properly
 * converted and rendered as their unicode counterparts in the chat history.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    /**
     * Ensures the chat queue is processed and checked every tick
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void melinoe$checkChatRefresh(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        ChatModule.INSTANCE.checkAndRefreshChat();
    }

    /**
     * Parses incoming chat to verify if the server moved us to a different channel (Guild/Party)
     */
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void melinoe$catchServerChatModeSwitch(Component message, CallbackInfo ci) {
        if (!ChatModule.INSTANCE.getEnabled()) return;

        // If it was a system message confirming a switch, cancel it from rendering visually
        if (ChatModule.INSTANCE.handleChatModeMessage(message.getString())) {
            ci.cancel();
        }
    }

    /**
     * Swaps plain text shortcodes into graphical Emoji representations in the incoming server text
     */
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component melinoe$replaceEmojisInChat(Component message) {
        return EmojiReplacer.INSTANCE.replaceIn(message);
    }

    /**
     * Condenses text into "..." if the hide Group or Guild content is turned on
     */
    @ModifyVariable(
            method = "addMessageToDisplayQueue",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private GuiMessage melinoe$hideTabSpecificMessages(GuiMessage message) {
        if (!ChatModule.INSTANCE.getEnabled()) return message;

        Component newContent = ChatModule.INSTANCE.processMessageContent(message.content());
        if (newContent != message.content()) {
            return new GuiMessage(message.addedTime(), newContent, message.signature(), message.tag());
        }
        return message;
    }

    /**
     * Blocks messages entirely from rendering if they don't belong in the current Active Tab
     */
    @Inject(
            method = "addMessageToDisplayQueue",
            at = @At("HEAD"),
            cancellable = true
    )
    private void melinoe$filterTabVisibility(GuiMessage message, CallbackInfo ci) {
        if (!ChatModule.INSTANCE.getEnabled()) return;

        String plainText = message.content().getString();
        if (!ChatModule.INSTANCE.shouldShowMessage(plainText)) {
            ci.cancel();
        }
    }
}
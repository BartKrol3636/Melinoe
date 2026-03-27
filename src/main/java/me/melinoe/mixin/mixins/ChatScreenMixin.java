package me.melinoe.mixin.mixins;

import me.melinoe.utils.emoji.EmojiShortcodes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow protected EditBox input;

    /**
     * Replaces standard shortcodes with actual Emoji icons the instant they are completed.
     * We hook `render` because ChatScreen does not override `tick()` in 1.21.10.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void melinoe$processLiveChatInput(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.input != null) {
            EmojiShortcodes.INSTANCE.processEditBox(this.input);
        }
    }

    /**
     * Safely reverts the unicode Emojis back into server-safe `:shortcodes:` upon sending.
     */
    @ModifyVariable(
            method = "handleChatInput",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private String melinoe$convertEmojisToShortcodes(String message) {
        return EmojiShortcodes.INSTANCE.replaceEmojiWithShortcodes(message);
    }
}
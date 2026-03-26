package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.misc.ChatEmojisModule;
import me.melinoe.utils.EmojiShortcodes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles emoji processing in EditBox fields.
 */
@Mixin(EditBox.class)
public class EditBoxMixin {

    @Shadow private String value;

    @ModifyVariable(
        method = "insertText",
        at = @At("HEAD"),
        argsOnly = true
    )
    private String melinoe$stripEmojiOnInsert(String text) {
        if (!ChatEmojisModule.INSTANCE.getEnabled()) {
            return text;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) {
            return text;
        }
        
        if (!(mc.screen instanceof ChatScreen)) {
            return EmojiShortcodes.replaceEmojiWithShortcodes(text);
        }
        
        return text;
    }

    @ModifyVariable(
        method = "setValue",
        at = @At("HEAD"),
        argsOnly = true
    )
    private String melinoe$processEmojisOnSet(String value) {
        if (!ChatEmojisModule.INSTANCE.getEnabled()) {
            return value;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null || value == null || value.isEmpty()) {
            return value;
        }
        
        if (mc.screen instanceof ChatScreen) {
            return EmojiShortcodes.replaceShortcodesWithEmojis(value);
        } else {
            return EmojiShortcodes.replaceEmojiWithShortcodes(value);
        }
    }
    
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
    private void melinoe$processEmojisOnGet(CallbackInfoReturnable<String> cir) {
        if (!ChatEmojisModule.INSTANCE.getEnabled()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) {
            return;
        }
        
        if (!(mc.screen instanceof ChatScreen)) {
            String currentValue = cir.getReturnValue();
            if (currentValue != null && !currentValue.isEmpty()) {
                String converted = EmojiShortcodes.replaceEmojiWithShortcodes(currentValue);
                if (!converted.equals(currentValue)) {
                    cir.setReturnValue(converted);
                    this.value = converted;
                }
            }
        }
    }
    
}

package me.melinoe.mixin.mixins;

import me.melinoe.features.impl.misc.KeybindsModule;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {

    /**
     * Intercepts chat component clicks before Minecraft validates the command
     */
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void melinoe$interceptTpClickEvents(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (style != null && style.getClickEvent() != null) {
            ClickEvent event = style.getClickEvent();

            // Check if it's a run command record
            if (event instanceof ClickEvent.RunCommand runCmd) {
                String cmd = runCmd.command();

                // If it's the teleport command, handle it internally and bypass the warning screen
                if (cmd.startsWith("/tp ")) {
                    String player = cmd.substring(4).trim();
                    if (KeybindsModule.INSTANCE.handleCalloutTeleport(player)) {
                        // Return true to bypass the confirmation screen
                        cir.setReturnValue(true);
                    }
                }
            }
        }
    }
}
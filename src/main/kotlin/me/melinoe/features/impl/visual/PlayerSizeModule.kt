package me.melinoe.features.impl.visual

import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.Melinoe
import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.DropdownSetting
import me.melinoe.clickgui.settings.impl.NumberSetting
import me.melinoe.clickgui.settings.impl.StringSetting
import me.melinoe.clickgui.settings.impl.ActionSetting
import me.melinoe.utils.Color
import me.melinoe.utils.Colors
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import com.mojang.authlib.GameProfile

/**
 * Player Size Module - changes the size of the player.
 */
object PlayerSizeModule : Module(
    name = "Player Size",
    category = Category.VISUAL,
    description = "Changes the size of the player."
) {
    private val devSizeX by NumberSetting("Size X", 1f, -1f, 3f, 0.1f, desc = "X scale of the dev size")
    private val devSizeY by NumberSetting("Size Y", 1f, -1f, 3f, 0.1f, desc = "Y scale of the dev size")
    private val devSizeZ by NumberSetting("Size Z", 1f, -1f, 3f, 0.1f, desc = "Z scale of the dev size")

    @JvmStatic
    fun preRenderCallbackScaleHook(entityRenderer: AvatarRenderState, matrix: PoseStack) {
        val gameProfile = entityRenderer.getData(GAME_PROFILE_KEY) ?: return
        val playerName = Melinoe.mc.player?.gameProfile?.name
        if (enabled && gameProfile.name == playerName) {
            if (devSizeY < 0) matrix.translate(0.0, (devSizeY * 2).toDouble(), 0.0)
            matrix.scale(devSizeX, devSizeY, devSizeZ)
        }
    }

    @JvmStatic
    val GAME_PROFILE_KEY: RenderStateDataKey<GameProfile> = RenderStateDataKey.create { "melinoe:game_profile" }
}

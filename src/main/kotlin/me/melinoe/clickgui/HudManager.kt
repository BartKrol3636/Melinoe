package me.melinoe.clickgui

import me.melinoe.Melinoe
import me.melinoe.features.ModuleManager
import me.melinoe.features.ModuleManager.hudSettingsCache
import me.melinoe.clickgui.settings.impl.HudElement
import me.melinoe.utils.Colors
import me.melinoe.utils.ui.mouseX as melinoeMouseX
import me.melinoe.utils.ui.mouseY as melinoeMouseY
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.sign

object HudManager : Screen(Component.literal("HUD Manager")) {

    private var dragging: HudElement? = null

    private var deltaX = 0f
    private var deltaY = 0f

    override fun init() {
        for (hud in hudSettingsCache) {
            if (hud.isEnabled) {
                val sw = Melinoe.mc.window.screenWidth
                val sh = Melinoe.mc.window.screenHeight
                val clampedScreenX = hud.value.screenX.coerceIn(0, (sw - (hud.value.width * hud.value.scale)).toInt())
                val clampedScreenY = hud.value.screenY.coerceIn(0, (sh - (hud.value.height * hud.value.scale)).toInt())
                hud.value.setScreenX(clampedScreenX)
                hud.value.setScreenY(clampedScreenY)
            }
        }
        super.init()
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.render(context, mouseX, mouseY, deltaTicks)

        dragging?.let {
            val sw = Melinoe.mc.window.screenWidth
            val sh = Melinoe.mc.window.screenHeight
            val newScreenX = (melinoeMouseX + deltaX).coerceIn(0f, (sw - (it.width * it.scale))).toInt()
            val newScreenY = (melinoeMouseY + deltaY).coerceIn(0f, (sh - (it.height * it.scale))).toInt()
            // Update anchors based on which half the element is on
            it.anchorRight = newScreenX + (it.width * it.scale) / 2f > sw / 2f
            it.anchorBottom = newScreenY + (it.height * it.scale) / 2f > sh / 2f
            it.setScreenX(newScreenX)
            it.setScreenY(newScreenY)
        }

        context.pose().pushMatrix()
        val sf = Melinoe.mc.window.guiScale
        context.pose().scale(1f / sf, 1f / sf)

        for (hud in hudSettingsCache) {
            if (hud.isEnabled) hud.value.draw(context, true)
        }

        hudSettingsCache.firstOrNull { it.isEnabled && it.value.isHovered() }?.let { hoveredHud ->
            context.pose().pushMatrix()
            context.pose().translate(
                (hoveredHud.value.screenX + hoveredHud.value.width * hoveredHud.value.scale + 10f),
                hoveredHud.value.screenY.toFloat(),
            )
            context.pose().scale(2f, 2f)
            context.drawString(Melinoe.mc.font, hoveredHud.name, 0, 0, Colors.WHITE.rgba)
            context.drawWordWrap(Melinoe.mc.font, Component.literal(hoveredHud.description), 0, 10, 150, Colors.WHITE.rgba)
            context.pose().popMatrix()
        }

        context.pose().popMatrix()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val actualAmount = verticalAmount.sign.toFloat() * 0.2f
        hudSettingsCache.firstOrNull { it.isEnabled && it.value.isHovered() }?.let { hovered ->
            hovered.value.scale = (hovered.value.scale + actualAmount).coerceIn(1f, 10f)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        hudSettingsCache.firstOrNull { it.isEnabled && it.value.isHovered() }?.let { hovered ->
            dragging = hovered.value

            deltaX = (hovered.value.screenX - melinoeMouseX)
            deltaY = (hovered.value.screenY - melinoeMouseY)
            return true
        }

        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        dragging = null
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        hudSettingsCache.firstOrNull { it.isEnabled && it.value.isHovered() }?.let { hovered ->
            when (keyEvent.key) {
                GLFW.GLFW_KEY_EQUAL -> hovered.value.scale = (hovered.value.scale + 0.1f).coerceIn(1f, 10f)
                GLFW.GLFW_KEY_MINUS -> hovered.value.scale = (hovered.value.scale - 0.1f).coerceIn(1f, 10f)
                GLFW.GLFW_KEY_RIGHT -> hovered.value.setScreenX(hovered.value.screenX + 10)
                GLFW.GLFW_KEY_LEFT -> hovered.value.setScreenX(hovered.value.screenX - 10)
                GLFW.GLFW_KEY_UP -> hovered.value.setScreenY(hovered.value.screenY - 10)
                GLFW.GLFW_KEY_DOWN -> hovered.value.setScreenY(hovered.value.screenY + 10)
            }
        }

        return super.keyPressed(keyEvent)
    }

    override fun onClose() {
        ModuleManager.saveConfigurations()
        super.onClose()
    }

    fun resetHUDS() {
        hudSettingsCache.forEach {
            it.value.x = 10
            it.value.y = 10
            it.value.scale = 2f
        }
    }

    override fun isPauseScreen(): Boolean = false
}

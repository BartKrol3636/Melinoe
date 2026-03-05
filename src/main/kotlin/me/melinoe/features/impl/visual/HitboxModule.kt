package me.melinoe.features.impl.visual

import me.melinoe.Melinoe.mc
import me.melinoe.events.RenderEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.ColorSetting
import me.melinoe.clickgui.settings.impl.DropdownSetting
import me.melinoe.clickgui.settings.impl.SelectorSetting
import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.utils.Color
import me.melinoe.utils.renderBoundingBox
import me.melinoe.utils.render.drawStyledBox
import net.minecraft.client.CameraType
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player

/**
 * Hitboxes Module - renders entity hitboxes with optional fill.
 * Similar to F3+B debug hitboxes but with customizable colors and fill.
 */
object HitboxModule : Module(
    name = "Hitboxes",
    category = Category.VISUAL,
    description = "Renders entity hitboxes with customizable colors and fill."
) {

    private val renderStyle by SelectorSetting("Render Style", "Filled Outline", listOf("Outline", "Filled Outline"), desc = "Style of the box.")
    private val color by ColorSetting("Color", Color(255, 255, 255, 1f), true, desc = "Color of the hitbox")
    private val renderPlayersSetting by BooleanSetting("Players", true, desc = "Render hitboxes for players")
    private val renderMobsSetting by BooleanSetting("Mobs", true, desc = "Render hitboxes for mobs")
    private val renderItemsSetting by BooleanSetting("Items", false, desc = "Render hitboxes for items")
    private val hideArmorStandsSetting by BooleanSetting("Hide Armor Stands", true, desc = "Don't render hitboxes for armor stands")
    
    // Telos Dropdown
    private val telosDropdown by DropdownSetting("Telos", false)
    private val renderTelosSetting by BooleanSetting("Enable Hitboxes", true, desc = "Render hitboxes for Telos entities (bosses, mobs, attacks.)").withDependency { telosDropdown }
    private val telosColor by ColorSetting("Telos Color", Color(255, 100, 100, 1f), true, desc = "Color for Telos hitboxes").withDependency { telosDropdown }

    init {
        on<RenderEvent.Extract> {
            if (!enabled) return@on // Don't render if module is disabled
            
            val level = mc.level ?: return@on
            val player = mc.player ?: return@on

            // Render player's own hitbox (only in third person)
            if (renderPlayersSetting && mc.options.cameraType != CameraType.FIRST_PERSON) {
                // Use interpolated render bounding box for smooth movement
                val box = player.renderBoundingBox
                
                drawStyledBox(box, color, renderStyle, true)
            }

            // Iterate through all other entities in the level
            for (entity in level.entitiesForRendering()) {
                // Skip the player itself (already rendered above)
                if (entity == player) continue

                // Skip armor stands if the setting is enabled
                if (entity is ArmorStand && hideArmorStandsSetting) continue

                // Determine if this is a "Telos" entity (other entity type)
                val isTelos = entity !is Player && entity !is net.minecraft.world.entity.Mob && entity !is ItemEntity
                
                // Filter entity types based on settings
                if (entity is Player && !renderPlayersSetting) continue
                if (entity is net.minecraft.world.entity.Mob && !renderMobsSetting) continue
                if (entity is ItemEntity && !renderItemsSetting) continue
                if (isTelos && !renderTelosSetting) continue

                // Use interpolated render bounding box for smooth movement
                val box = entity.renderBoundingBox
                
                // Skip hitboxes that are too thin (like interaction entities)
                // These render as lines which look like artifacts
                val width = box.maxX - box.minX
                val height = box.maxY - box.minY
                val depth = box.maxZ - box.minZ
                val minSize = 0.01 // Minimum size threshold
                
                if (width < minSize || height < minSize || depth < minSize) continue

                // Use Telos color if this is a Telos entity, otherwise use default color
                val boxColor = if (isTelos) telosColor else color

                drawStyledBox(box, boxColor, renderStyle, true)
            }
        }
    }
}

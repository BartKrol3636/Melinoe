package me.melinoe.features.impl.visual.dungeontimer

import me.melinoe.utils.PersonalBestManager
import me.melinoe.utils.data.BossData
import me.melinoe.utils.data.DungeonData
import me.melinoe.utils.toNative
import net.minecraft.network.chat.Component

/**
 * Formats dungeon completion and split messages with personal best comparisons.
 */
object MessageFormatter {
    
    /**
     * Helper to convert an integer color to a MiniMessage hex tag
     */
    private fun Int.toHexTag(): String = "<#${String.format("%06X", this and 0xFFFFFF)}>"
    
    /**
     * Formats a dungeon completion message with boss defeat and PB comparison.
     */
    fun formatCompletionMessage(
        dungeon: DungeonData,
        time: Float,
        oldPB: Float,
        isNewPB: Boolean
    ): Component {
        val bossColor = GradientTextBuilder.getBrightColor(dungeon.dungeonType).toHexTag()
        val timeColor = GradientTextBuilder.getDarkColor(dungeon.dungeonType).toHexTag()
        
        val bossName = dungeon.finalBoss.label
        val timeStr = PersonalBestManager.formatTimeWithDecimals(time)
        val pbString = getPBComparisonString(dungeon.areaName, time, oldPB, isNewPB)
        
        return "${Constants.ICON_SKULL} <gray>Defeated $bossColor$bossName <gray>in $timeColor$timeStr$pbString".toNative()
    }
    
    /**
     * Formats a boss split message (for multi-stage dungeons).
     */
    fun formatSplitMessage(
        dungeon: DungeonData,
        boss: BossData,
        time: Float,
        oldPB: Float,
        isNewPB: Boolean
    ): Component {
        val bossColor = GradientTextBuilder.getBrightColor(dungeon.dungeonType).toHexTag()
        val timeColor = GradientTextBuilder.getDarkColor(dungeon.dungeonType).toHexTag()
        
        val timeStr = PersonalBestManager.formatTimeWithDecimals(time)
        val pbString = getPBComparisonString(dungeon.areaName, time, oldPB, isNewPB)
        
        return "${Constants.ICON_SPLIT} Split: <gray>Defeated $bossColor${boss.label} <gray>in $timeColor$timeStr$pbString".toNative()
    }
    
    /**
     * Formats a boss split summary message (shown at the end of multi-stage dungeons).
     */
    fun formatSplitSummaryMessage(
        dungeon: DungeonData,
        boss: BossData,
        splitTime: Float,
        oldPB: Float,
        wasNewPB: Boolean
    ): Component {
        val bossColor = GradientTextBuilder.getBrightColor(dungeon.dungeonType).toHexTag()
        val timeColor = GradientTextBuilder.getDarkColor(dungeon.dungeonType).toHexTag()
        
        val timeStr = PersonalBestManager.formatTimeWithDecimals(splitTime)
        val pbString = getPBComparisonString(dungeon.areaName,  splitTime, oldPB, wasNewPB)
        
        return "${Constants.ICON_SKULL} <gray>Defeated $bossColor${boss.label} <gray>in $timeColor$timeStr$pbString".toNative()
    }
    
    /**
     * Formats the personal best comparison section of a message.
     */
    private fun getPBComparisonString(dungeon: String, time: Float, oldPB: Float, isNewPB: Boolean): String {
        if (isNewPB) {
            val timeStr = PersonalBestManager.formatTimeWithDecimals(time)
            val hasOldPB = oldPB != -1f
            
            val shareText: String
            val improveText: String
            
            if (hasOldPB) {
                val diff = time - oldPB
                val diffStr = PersonalBestManager.formatTimeDifferenceWithDecimals(diff)
                val oldPBStr = PersonalBestManager.formatTimeWithDecimals(oldPB)
                
                shareText = "NEW RECORD! Completed $dungeon in $timeStr! (Old: $oldPBStr | $diffStr)"
                improveText = " <dark_gray>(<green>$diffStr<dark_gray>)"
            } else {
                shareText = "NEW RECORD! Completed $dungeon in $timeStr!"
                improveText = ""
            }
            
            val safeShareText = shareText.replace("'", "\\'")
            
            val shareButton = " <click:suggest_command:'$safeShareText'><hover:show_text:\"<gray>Click to share in chat!</gray>\"><gray><b>⧉</b></gray></hover></click>"
            val improvement = "$improveText$shareButton"
            
            return " ${Constants.ICON_FIRE} <gold><bold>NEW RECORD!</bold></gold>$improvement"
        }
        
        if (oldPB != -1f) {
            val difference = time - oldPB
            val diffColor = if (difference > 0) "<red>" else "<green>"
            
            val oldPBStr = PersonalBestManager.formatTimeWithDecimals(oldPB)
            val diffStr = PersonalBestManager.formatTimeDifferenceWithDecimals(difference)
            
            return " <dark_gray>(${Constants.ICON_STAR} <gray>$oldPBStr <dark_gray>| $diffColor$diffStr<dark_gray>)"
        }
        
        return ""
    }
}
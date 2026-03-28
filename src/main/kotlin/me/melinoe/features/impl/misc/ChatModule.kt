package me.melinoe.features.impl.misc

import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.DropdownSetting
import me.melinoe.clickgui.settings.impl.KeybindSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.emoji.EmojiShortcodes
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.client.gui.GuiGraphics
import org.lwjgl.glfw.GLFW
import java.io.File
import java.util.Optional
import java.util.concurrent.ConcurrentLinkedQueue

enum class ChatTab(val displayName: String) {
    ALL("All"), CHAT("Chat"), GROUP("Group"), GUILD("Guild"),
    MESSAGES("Messages"), DROPS("Drops"), DEATHS("Deaths"), CRAFTS("Crafts"), UTILITY("Utility")
}

enum class ServerChatCategory(val id: String) {
    DEFAULT("default"), GUILD("guild"), GROUP("group")
}

private data class QueuedMessage(val targetCategory: ServerChatCategory, val message: String)

// State machine for safely switching server channels, sending a message, and switching back
private enum class QueueState {
    IDLE,
    WAITING_FOR_SWITCH,
    READY_TO_SEND_MSG,
    WAITING_FOR_RESTORE
}

object ChatModule : Module(
    name = "Chat",
    category = Category.MISC,
    description = "Adds tabs, filtering, and hiding of messages"
) {
    private val hideUtilityMessages by BooleanSetting("Hide Utility Messages", true, "Hides auction, fame, and auto-sell messages")
    private val enableChatTabs by BooleanSetting("Enable Chat Tabs", true, "Enable the chat tab grouping system")
    
    private val tabsDropdown by DropdownSetting("Chat Tabs", false).withDependency { enableChatTabs }
    private val showChatTab by BooleanSetting("Show Chat Tab", false, "Display the Chat tab").withDependency { tabsDropdown && enableChatTabs }
    private val showGroupTab by BooleanSetting("Show Group Tab", false, "Display the Group tab").withDependency { tabsDropdown && enableChatTabs }
    private val showGuildTab by BooleanSetting("Show Guild Tab", true, "Display the Guild tab").withDependency { tabsDropdown && enableChatTabs }
    private val showMessagesTab by BooleanSetting("Show Messages Tab", true, "Display the Messages tab").withDependency { tabsDropdown && enableChatTabs }
    private val showDropsTab by BooleanSetting("Show Drops Tab", true, "Display the Drops tab").withDependency { tabsDropdown && enableChatTabs }
    private val showDeathsTab by BooleanSetting("Show Deaths Tab", false, "Display the Deaths tab").withDependency { tabsDropdown && enableChatTabs }
    private val showCraftsTab by BooleanSetting("Show Crafts Tab", false, "Display the Crafts tab").withDependency { tabsDropdown && enableChatTabs }
    
    var hideGroupContent = false; private set
    var hideGuildContent = false; private set
    
    private val toggleHideGroupKey by KeybindSetting("Censor Group", GLFW.GLFW_KEY_UNKNOWN, desc = "Toggle replacing group messages with ...")
        .onPress {
            hideGroupContent = !hideGroupContent
            Minecraft.getInstance().gui?.chat?.rescaleChat()
        }
    
    private val toggleHideGuildKey by KeybindSetting("Censor Guild", GLFW.GLFW_KEY_UNKNOWN, desc = "Toggle replacing guild messages with ...")
        .onPress {
            hideGuildContent = !hideGuildContent
            Minecraft.getInstance().gui?.chat?.rescaleChat()
        }
    
    private val switchGuildChatKey by KeybindSetting("Toggle Guild", GLFW.GLFW_KEY_UNKNOWN, desc = "Switch between default and guild chat")
        .onPress { toggleServerChatMode(ServerChatCategory.GUILD) }
    
    private val switchGroupChatKey by KeybindSetting("Toggle Group", GLFW.GLFW_KEY_UNKNOWN, desc = "Switch between default and group chat")
        .onPress { toggleServerChatMode(ServerChatCategory.GROUP) }
    
    var activeTab: ChatTab = ChatTab.ALL
        private set
    
    private var lastTabState: ChatTab = ChatTab.ALL
    private var lastEnabledState: Boolean = false
    private var scrollOffset = 0
    
    // Cache regexes
    private val contentRegex = Regex("^(.*?\\[(?:Group|Guild)\\].*?:\\s*)", RegexOption.IGNORE_CASE)
    private val modeSwitchRegex = Regex("^Set your chat mode to (\\w+)\\.", RegexOption.IGNORE_CASE)
    private val fameRegex = Regex("^\\+\\d+ Fame gained!$")
    private val autoSellRegex = Regex("^Auto-sell earnings: .+$")
    
    var currentServerCategory = ServerChatCategory.DEFAULT
        private set
    
    private val messageQueue = ConcurrentLinkedQueue<QueuedMessage>()
    private var queueState = QueueState.IDLE
    private var originalCategory = ServerChatCategory.DEFAULT
    private var pendingMessage = ""
    private var stateStartTime = 0L
    private var lastMessageTime = 0L
    
    init {
        loadChatCategory()
    }
    
    // Build the list once
    private val availableTabs: List<ChatTab>
        get() = buildList(9) {
            add(ChatTab.ALL)
            if (showChatTab) add(ChatTab.CHAT)
            if (showGroupTab) add(ChatTab.GROUP)
            if (showGuildTab) add(ChatTab.GUILD)
            if (showMessagesTab) add(ChatTab.MESSAGES)
            if (showDropsTab) add(ChatTab.DROPS)
            if (showDeathsTab) add(ChatTab.DEATHS)
            if (showCraftsTab) add(ChatTab.CRAFTS)
        }
    
    private fun setActiveTab(tab: ChatTab) {
        if (activeTab != tab) {
            activeTab = tab
            ensureActiveTabVisible()
            Minecraft.getInstance().gui?.chat?.rescaleChat() // Force chat history to re-render for the new tab
        }
    }
    
    fun enqueueMessage(category: ServerChatCategory, message: String) {
        messageQueue.add(QueuedMessage(category, message))
    }
    
    private fun toggleServerChatMode(target: ServerChatCategory) {
        val mc = Minecraft.getInstance()
        if (currentServerCategory == target) {
            mc.connection?.sendCommand("chat default")
        } else {
            mc.connection?.sendCommand("chat ${target.id}")
        }
    }
    
    fun recordNativeMessageSent(message: String) {
        // Prevent bypassing chat cooldowns by only tracking non-command messages
        if (!message.startsWith("/")) {
            lastMessageTime = System.currentTimeMillis()
        }
    }
    
    // Handles the automated switching of chat modes to send queued messages
    private fun tickQueue() {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        val connection = mc.connection ?: return
        val now = System.currentTimeMillis()
        
        // Reset if the server doesn't respond to the mode switch command
        if (queueState != QueueState.IDLE && now - stateStartTime > 3000) {
            queueState = QueueState.IDLE
        }
        
        if (queueState == QueueState.IDLE && messageQueue.isNotEmpty()) {
            val next = messageQueue.peek() ?: return
            
            if (currentServerCategory == next.targetCategory) {
                // Already in the right mode, send the message while respecting server cooldown
                if (now - lastMessageTime >= 1100) {
                    messageQueue.poll()
                    connection.sendChat(next.message)
                    lastMessageTime = now
                }
            } else {
                // Need to switch modes first
                originalCategory = currentServerCategory
                pendingMessage = next.message
                queueState = QueueState.WAITING_FOR_SWITCH
                stateStartTime = now
                connection.sendCommand("chat ${next.targetCategory.id}")
            }
        }
        else if (queueState == QueueState.READY_TO_SEND_MSG) {
            if (now - lastMessageTime >= 1100) {
                messageQueue.poll()
                connection.sendChat(pendingMessage)
                lastMessageTime = now
                
                // Switch back to original mode
                queueState = QueueState.WAITING_FOR_RESTORE
                stateStartTime = now
                connection.sendCommand("chat ${originalCategory.id}")
            }
        }
    }
    
    // Parses incoming server messages to detect chat mode switches
    fun handleChatModeMessage(plainText: String): Boolean {
        val match = modeSwitchRegex.find(plainText) ?: return false
        val newMode = match.groupValues[1].lowercase()
        
        currentServerCategory = ServerChatCategory.entries.find { it.id == newMode } ?: ServerChatCategory.DEFAULT
        saveChatCategory()
        
        if (queueState == QueueState.WAITING_FOR_SWITCH) {
            queueState = QueueState.READY_TO_SEND_MSG
            return true // Hide the mode switch message
        }
        
        if (queueState == QueueState.WAITING_FOR_RESTORE) {
            queueState = QueueState.IDLE
            return true
        }
        
        return false
    }
    
    fun interceptOutgoingMessage(rawMessage: String, addToHistory: Boolean): Boolean {
        if (!enabled || rawMessage.startsWith("/")) return false
        
        // If we are busy switching modes, intercept standard chat and queue it
        if (queueState != QueueState.IDLE) {
            val processedMessage = EmojiShortcodes.replaceEmojiWithShortcodes(rawMessage)
            enqueueMessage(originalCategory, processedMessage)
            if (addToHistory) Minecraft.getInstance().gui?.chat?.addRecentChat(rawMessage)
            return true
        }
        return false
    }
    
    private fun saveChatCategory() {
        runCatching {
            val file = File(Minecraft.getInstance().gameDirectory, "config/chat_state.txt")
            file.parentFile.mkdirs()
            file.writeText(currentServerCategory.id)
        }
    }
    
    private fun loadChatCategory() {
        runCatching {
            val file = File(Minecraft.getInstance().gameDirectory, "config/chat_state.txt")
            if (file.exists()) {
                val saved = file.readText().trim()
                currentServerCategory = ServerChatCategory.entries.find { it.id == saved } ?: ServerChatCategory.DEFAULT
            }
        }
    }
    
    fun checkAndRefreshChat() {
        val currentEnabled = enabled
        
        // Handle toggling module on/off
        if (lastEnabledState != currentEnabled) {
            lastEnabledState = currentEnabled
            if (!currentEnabled) activeTab = ChatTab.ALL
            Minecraft.getInstance().gui?.chat?.rescaleChat()
        }
        
        if (!currentEnabled) return
        
        if (!enableChatTabs && activeTab != ChatTab.ALL) {
            activeTab = ChatTab.ALL
            Minecraft.getInstance().gui?.chat?.rescaleChat()
        }
        
        if (lastTabState != activeTab) {
            lastTabState = activeTab
            Minecraft.getInstance().gui?.chat?.rescaleChat()
        }
        
        tickQueue()
    }
    
    private val deathCauses = arrayOf(
        "fell off against", "had their bits blown off by", "was torn in half by",
        "was spangled by", "got snapped in half by", "had their head removed by",
        "was diagnosed with 'skill issue' by", "dieded by", "had their day ruined by"
    )
    
    // Sorts a text message into its respective tab category
    private fun determineCategory(message: String): ChatTab {
        if (message == "Auction items have been updated." || fameRegex.matches(message) || autoSellRegex.matches(message))
            return ChatTab.UTILITY
        
        if (message.contains(" has just crafted ")) return ChatTab.CRAFTS
        if (message.contains(" got ") && message.contains(" from ")) return ChatTab.DROPS
        
        for (cause in deathCauses) {
            if (message.contains(" $cause ")) return ChatTab.DEATHS
        }
        
        if (message.contains("[Group]")) return ChatTab.GROUP
        if (message.contains("[Guild]")) return ChatTab.GUILD
        if ((message.contains("To ") || message.contains("From ")) && message.contains("┅")) return ChatTab.MESSAGES
        if (message.contains(": ")) return ChatTab.CHAT
        
        return ChatTab.ALL
    }
    
    fun shouldShowMessage(plainText: String): Boolean {
        if (!enabled) return true
        val category = determineCategory(plainText)
        
        if (hideUtilityMessages && category == ChatTab.UTILITY) return false
        if (!enableChatTabs || activeTab == ChatTab.ALL) return category != ChatTab.UTILITY
        
        return category == activeTab
    }
    
    // Allows censoring group/guild messages
    fun processMessageContent(original: Component): Component {
        if (!enabled) return original
        
        val plainText = original.string
        val category = determineCategory(plainText)
        
        val shouldHide = (hideGroupContent && category == ChatTab.GROUP) ||
                (hideGuildContent && category == ChatTab.GUILD)
        
        if (!shouldHide) return original
        
        val match = contentRegex.find(plainText) ?: return original
        val prefixLength = match.value.length
        
        val newComp = Component.empty()
        var currentLen = 0
        var appendedDots = false
        
        // Rebuilds the component keeping the original styles intact, appending "..." when needed
        original.visit({ style: Style, text: String ->
            if (appendedDots) return@visit Optional.empty<Unit>()
            
            val remaining = prefixLength - currentLen
            if (remaining > 0) {
                if (text.length <= remaining) {
                    newComp.append(Component.literal(text).withStyle(style))
                    currentLen += text.length
                } else {
                    val portion = text.substring(0, remaining)
                    newComp.append(Component.literal(portion).withStyle(style))
                    currentLen += portion.length
                    newComp.append(Component.literal("...").withStyle(style))
                    appendedDots = true
                }
            } else {
                newComp.append(Component.literal("...").withStyle(style))
                appendedDots = true
            }
            Optional.empty<Unit>()
        }, Style.EMPTY)
        
        if (!appendedDots) newComp.append(Component.literal("..."))
        
        return newComp
    }
    
    private fun ensureActiveTabVisible() {
        val mc = Minecraft.getInstance()
        val font = mc.font
        val maxWidth = mc.gui.chat.width
        
        var currentX = 0
        val tabPadding = 2
        val tabSpacing = 2
        
        for (tab in availableTabs) {
            val w = font.width(tab.displayName) + (tabPadding * 2)
            if (tab == activeTab) {
                if (currentX < scrollOffset) {
                    scrollOffset = currentX
                } else if (currentX + w > scrollOffset + maxWidth) {
                    scrollOffset = (currentX + w) - maxWidth
                }
                break
            }
            currentX += w + tabSpacing
        }
    }
    
    fun renderTabs(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!enabled || !enableChatTabs) return
        
        val mc = Minecraft.getInstance()
        val font = mc.font
        val maxWidth = mc.gui.chat.width
        val currentY = mc.window.guiScaledHeight - 32
        val tabPadding = 2
        val tabSpacing = 2
        
        guiGraphics.enableScissor(4, currentY - 2, 4 + maxWidth, currentY + font.lineHeight + tabPadding * 2 + 2)
        
        var currentX = 4 - scrollOffset
        
        for (tab in availableTabs) {
            val tabWidth = font.width(tab.displayName) + (tabPadding * 2)
            
            // Only calculate hovered state and render if it sits within visible bounds
            if (currentX + tabWidth > 4 && currentX < 4 + maxWidth) {
                val isHovered = mouseX >= currentX && mouseX <= currentX + tabWidth && mouseY >= currentY && mouseY <= currentY + font.lineHeight + (tabPadding * 2)
                
                val textColor = when {
                    tab == activeTab -> -1 // White
                    isHovered -> 0xFFEEEEEE.toInt() // Light gray
                    else -> 0xFFAAAAAA.toInt() // Normal gray
                }
                
                guiGraphics.drawString(font, tab.displayName, currentX + tabPadding, currentY + tabPadding, textColor, true)
            }
            
            currentX += tabWidth + tabSpacing
        }
        
        guiGraphics.disableScissor()
    }
    
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!enabled || !enableChatTabs || button != 0) return false
        
        val mc = Minecraft.getInstance()
        val font = mc.font
        val maxWidth = mc.gui.chat.width
        val currentY = mc.window.guiScaledHeight - 32
        val tabPadding = 2
        val tabSpacing = 2
        
        if (mouseX < 4 || mouseX > 4 + maxWidth) return false
        
        var currentX = 4 - scrollOffset
        for (tab in availableTabs) {
            val tabWidth = font.width(tab.displayName) + (tabPadding * 2)
            
            if (mouseX >= currentX && mouseX <= currentX + tabWidth && mouseY >= currentY && mouseY <= currentY + font.lineHeight + (tabPadding * 2)) {
                setActiveTab(tab)
                return true
            }
            currentX += tabWidth + tabSpacing
        }
        return false
    }
    
    fun keyPressed(keyCode: Int, modifiers: Int): Boolean {
        if (!enabled || !enableChatTabs) return false
        
        // Add in support for Alt + Left/Right arrow keys to swap between chat tabs
        if ((modifiers and GLFW.GLFW_MOD_ALT) != 0) {
            val tabs = availableTabs
            val currentIndex = tabs.indexOf(activeTab)
            
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                val newIndex = if (currentIndex > 0) currentIndex - 1 else tabs.size - 1
                setActiveTab(tabs[newIndex])
                return true
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                val newIndex = if (currentIndex < tabs.size - 1) currentIndex + 1 else 0
                setActiveTab(tabs[newIndex])
                return true
            }
        }
        return false
    }
}
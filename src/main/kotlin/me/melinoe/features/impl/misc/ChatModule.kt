package me.melinoe.features.impl.misc

import me.melinoe.clickgui.settings.Setting.Companion.withDependency
import me.melinoe.clickgui.settings.impl.BooleanSetting
import me.melinoe.clickgui.settings.impl.DropdownSetting
import me.melinoe.clickgui.settings.impl.KeybindSetting
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.emoji.EmojiShortcodes
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
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
    
    // UI Render Caches
    private var cachedAvailableTabs: List<ChatTab> = emptyList()
    private var lastTabSettingsHash = -1
    private val tabWidthCache = mutableMapOf<ChatTab, Int>()
    
    // Cache regexes
    private val contentRegex = Regex("^(.*?\\[(?:Group|Guild)\\].*?:\\s*)", RegexOption.IGNORE_CASE)
    private val modeSwitchRegex = Regex("^Set your chat mode to (\\w+)\\.", RegexOption.IGNORE_CASE)
    private val fameRegex = Regex("^\\+\\d+ Fame gained!$")
    private val autoSellRegex = Regex("^Auto-sell earnings: .+$")
    
    private val deathCauses = arrayOf(
        " fell off against ", " had their bits blown off by ", " was torn in half by ",
        " was spangled by ", " got snapped in half by ", " had their head removed by ",
        " was diagnosed with 'skill issue' by ", " dieded by ", " had their day ruined by "
    )
    
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
    
    // Evaluates a bitmask hash to only rebuild the List when a setting physically changes
    private val availableTabs: List<ChatTab>
        get() {
            var hash = 0
            if (showChatTab) hash = hash or 1
            if (showGroupTab) hash = hash or 2
            if (showGuildTab) hash = hash or 4
            if (showMessagesTab) hash = hash or 8
            if (showDropsTab) hash = hash or 16
            if (showDeathsTab) hash = hash or 32
            if (showCraftsTab) hash = hash or 64
            
            if (hash != lastTabSettingsHash) {
                lastTabSettingsHash = hash
                cachedAvailableTabs = buildList(9) {
                    add(ChatTab.ALL)
                    if (showChatTab) add(ChatTab.CHAT)
                    if (showGroupTab) add(ChatTab.GROUP)
                    if (showGuildTab) add(ChatTab.GUILD)
                    if (showMessagesTab) add(ChatTab.MESSAGES)
                    if (showDropsTab) add(ChatTab.DROPS)
                    if (showDeathsTab) add(ChatTab.DEATHS)
                    if (showCraftsTab) add(ChatTab.CRAFTS)
                }
            }
            return cachedAvailableTabs
        }
    
    private fun getTabWidth(tab: ChatTab, font: Font): Int {
        return tabWidthCache.getOrPut(tab) { font.width(tab.displayName) }
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
    
    private fun tickQueue() {
        if (!enabled) return
        
        if (queueState == QueueState.IDLE && messageQueue.isEmpty()) return
        
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
                if (now - lastMessageTime >= 1100) {
                    messageQueue.poll()
                    connection.sendChat(next.message)
                    lastMessageTime = now
                }
            } else {
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
                
                queueState = QueueState.WAITING_FOR_RESTORE
                stateStartTime = now
                connection.sendCommand("chat ${originalCategory.id}")
            }
        }
    }
    
    fun handleChatModeMessage(plainText: String): Boolean {
        val match = modeSwitchRegex.find(plainText) ?: return false
        val newMode = match.groupValues[1].lowercase()
        
        currentServerCategory = ServerChatCategory.entries.find { it.id == newMode } ?: ServerChatCategory.DEFAULT
        saveChatCategory()
        
        if (queueState == QueueState.WAITING_FOR_SWITCH) {
            queueState = QueueState.READY_TO_SEND_MSG
            return true
        }
        
        if (queueState == QueueState.WAITING_FOR_RESTORE) {
            queueState = QueueState.IDLE
            return true
        }
        
        return false
    }
    
    fun interceptOutgoingMessage(rawMessage: String, addToHistory: Boolean): Boolean {
        if (!enabled || rawMessage.startsWith("/")) return false
        
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
        var needsRescale = false
        
        if (lastEnabledState != currentEnabled) {
            lastEnabledState = currentEnabled
            if (!currentEnabled) activeTab = ChatTab.ALL
            needsRescale = true
        }
        
        if (!currentEnabled) {
            if (needsRescale) Minecraft.getInstance().gui?.chat?.rescaleChat()
            return
        }
        
        if (!enableChatTabs && activeTab != ChatTab.ALL) {
            activeTab = ChatTab.ALL
            needsRescale = true
        }
        
        val tabs = availableTabs
        if (activeTab != ChatTab.ALL && activeTab !in tabs) {
            activeTab = ChatTab.ALL
            needsRescale = true
        }
        
        if (lastTabState != activeTab) {
            lastTabState = activeTab
            needsRescale = true
        }
        
        if (needsRescale) {
            ensureActiveTabVisible()
            Minecraft.getInstance().gui?.chat?.rescaleChat()
        }
        
        tickQueue()
    }
    
    private fun determineCategory(message: String): ChatTab {
        // Fast checks first to avoid unnecessary processing
        if (message == "Auction items have been updated." || fameRegex.matches(message) || autoSellRegex.matches(message)) {
            return ChatTab.UTILITY
        }
        
        if (message.contains(" has just crafted ")) return ChatTab.CRAFTS
        
        if ((message.contains(" got ") && message.contains(" from ")) ||
            (message.contains(" - Dropped ") && message.contains(" pity from "))) {
            return ChatTab.DROPS
        }
        
        if (message.contains("[Group]")) return ChatTab.GROUP
        if (message.contains("[Guild]")) return ChatTab.GUILD
        
        if ((message.contains("To ") || message.contains("From ")) && message.contains("┅")) return ChatTab.MESSAGES
        
        for (i in deathCauses.indices) {
            if (message.contains(deathCauses[i])) return ChatTab.DEATHS
        }
        
        if (message.contains(": ")) return ChatTab.CHAT
        
        return ChatTab.ALL
    }
    
    fun shouldShowMessage(plainText: String): Boolean {
        if (!enabled) return true
        val category = determineCategory(plainText)
        
        if (category == ChatTab.UTILITY && hideUtilityMessages) {
            return false
        }
        
        if (!enableChatTabs || activeTab == ChatTab.ALL) {
            return true
        }
        
        return category == activeTab
    }
    
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
        
        val tabs = availableTabs
        for (tab in tabs) {
            val w = getTabWidth(tab, font) + (tabPadding * 2)
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
        val tabHeight = font.lineHeight + (tabPadding * 2)
        
        guiGraphics.enableScissor(4, currentY - 2, 4 + maxWidth, currentY + tabHeight + 2)
        
        var currentX = 4 - scrollOffset
        val tabs = availableTabs
        
        for (tab in tabs) {
            val tabWidth = getTabWidth(tab, font) + (tabPadding * 2)
            
            if (currentX + tabWidth > 4 && currentX < 4 + maxWidth) {
                val isHovered = mouseX >= currentX && mouseX <= currentX + tabWidth && mouseY >= currentY && mouseY <= currentY + tabHeight
                
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
        val tabHeight = font.lineHeight + (tabPadding * 2)
        
        // Immediately reject clicks that are visibly out of bounds
        if (mouseX < 4 || mouseX > 4 + maxWidth || mouseY < currentY || mouseY > currentY + tabHeight) return false
        
        var currentX = 4 - scrollOffset
        val tabs = availableTabs
        
        for (tab in tabs) {
            val tabWidth = getTabWidth(tab, font) + (tabPadding * 2)
            
            if (mouseX >= currentX && mouseX <= currentX + tabWidth) {
                setActiveTab(tab)
                return true
            }
            currentX += tabWidth + tabSpacing
        }
        return false
    }
    
    fun keyPressed(keyCode: Int, modifiers: Int): Boolean {
        if (!enabled || !enableChatTabs) return false
        
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
package me.melinoe.features.impl.misc

import me.melinoe.features.Category
import me.melinoe.features.Module

/**
 * Chat Emojis Module - replaces :shortcodes: with emojis in chat
 */
object ChatEmojisModule : Module(
    name = "Chat Emojis",
    category = Category.MISC,
    description = "Replace :shortcodes: with emojis in chat"
)

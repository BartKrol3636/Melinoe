package me.melinoe.utils.data

/**
 * Boss data enum containing all bosses in Telos.
 */
enum class PortalData(
    val label: String
) {
    // Lowlands
    SKULL_CAVERN("Skull Cavern"),
    THORNWOOD_WARGROVE("Thornwood Wargrove"),
    GOBLIN_LAIR("Goblin Lair"),
    DESERT_TEMPLE("Desert Temple"),
    TOMB_OF_SHADOWS("Tomb of Shadows"),
    SAKURA_SHRINE("Sakura Shrine"),
    SECLUDED_WOODLAND("Secluded Woodland"),
    
    // Centre
    ABYSS_OF_DEMONS("Abyss of Demons"),
    UNDEAD_LAIR("Undead Lair"),
    ICE_CAVE("Ice Cave"),
    DWARVEN_FROSTKEEP("Dwarven Frostkeep"),
    TREASURE_CAVE("Treasure Cave"),
    DEPTHS_OF_PURGATORY("Depths of Purgatory"),
    FROZEN_RUINS("Frozen Ruins"),
    KOBOLDS_DEN("Kobold's Den"),
    
    // Higher Dungeons
    OMNIPOTENTS_CITADEL("Omnipotent's Citadel"),
    FUNGAL_CAVERN("Fungal Cavern"),
    CORSAIRS_CONDUCTORIUM("Corsair's Conductorium"),
    FREDDYS_PIZZERIA("Freddy's Pizzeria"),
    CHRONOS("Chronos"),
    ANUBIS_LAIR("Anubis Lair"),
    TARTARUS("Tartarus"),
    CULTISTS_HIDEOUT("Cultist's Hideout"),
    ILLARIUS_HIDEOUT("Illarius' Hideout"),
    AURORA_SANCTUM("Aurora Sanctum"),
    AVIARY("The Aviary"),
    ONYXS_CASTLE("Raphael's Castle"),
    SHATTERS("Rustborn Kingdom"),
    
    // Shadowlands
    DREADWOOD_THICKET("Dreadwood Thicket"),
    RESOUNDING_RUINS("Resounding Ruins"),
    CELESTIALS_PROVINCE("Celestial's Province"),
    
    // Other Dungeons - These are dungeons that aren't supposed to be accessible directly but added nonetheless
    ONYXS_CASTLE2("Raphael's Chamber"),
    SHATTERS2("Rustborn Kingdom (Nebula)"),
    SHATTERS3("Rustborn Kingdom (Ophanim)"),
    TENEBRIS("Tenebris"),
    HARDMODE_CELESTIALS_PROVINCE_LOCK("Seraph's Domain (Locked)"),
    HARDMODE_CELESTIALS_PROVINCE("Seraph's Domain"),
    HARDMODE_SHATTERS_LOCK("Dawn of Creation (Locked)"),
    HARDMODE_SHATTERS("Dawn of Creation"),
    
    // Legacy
    VOID("Void"),
    PEAKS_OF_PURGATORY("Peaks of Purgatory");
}

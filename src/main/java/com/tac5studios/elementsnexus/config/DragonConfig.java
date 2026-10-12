package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/elements_nexus/dragon.toml - Ender Dragon auto respawn. Switch: features.toml [dragon] auto_respawn. */
public final class DragonConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue RESPAWN_MINUTES;
    public static final ModConfigSpec.ConfigValue<String> RESPAWN_MESSAGE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("respawn");
        RESPAWN_MINUTES = b.comment(
                "Minutes after the Ender Dragon dies before it respawns.",
                "The respawn starts once the time is up and a player is in The End near the exit portal,",
                "so the arena is loaded and the vanilla respawn (crystals, pillars, new gateway) can play out.",
                "The timer is saved, so a restart doesn't reset it."
        ).defineInRange("minutes", 30, 1, 10080);
        RESPAWN_MESSAGE = b.comment(
                "Message sent to everyone when the respawn starts. Supports & color codes. Empty = no message."
        ).define("message", "&5The Ender Dragon stirs... it returns to The End!");
        b.pop();

        SPEC = b.build();
    }

    private DragonConfig() {}
}

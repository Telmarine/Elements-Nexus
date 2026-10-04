package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/elements_nexus/afk.toml */
public final class AfkConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue IDLE_MINUTES;
    public static final ModConfigSpec.IntValue KICK_MINUTES;
    public static final ModConfigSpec.ConfigValue<String> NOW_AFK;
    public static final ModConfigSpec.ConfigValue<String> BACK;
    public static final ModConfigSpec.ConfigValue<String> KICK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        IDLE_MINUTES = b.comment("Minutes without moving, looking around, chatting or using commands before a player is AFK.")
                .defineInRange("idle_minutes", 5, 1, 120);
        KICK_MINUTES = b.comment("Minutes AFK before a player is kicked. Only used when afk_kick is on in features.toml.")
                .defineInRange("kick_minutes", 30, 1, 1440);
        NOW_AFK = b.comment("Message when a player goes AFK. {player} = their name. Turn these off with afk.announce in features.toml.")
                .define("now_afk", "&7{player} is now AFK.");
        BACK = b.comment("Message when a player comes back.")
                .define("back", "&7{player} is no longer AFK.");
        KICK = b.comment("Message shown to a player kicked for being AFK.")
                .define("kick_message", "You were AFK for too long.");

        SPEC = b.build();
    }

    private AfkConfig() {}
}

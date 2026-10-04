package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/elements_nexus/nicknames.toml */
public final class NickConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MIN_LENGTH;
    public static final ModConfigSpec.IntValue MAX_LENGTH;
    public static final ModConfigSpec.ConfigValue<String> ALLOWED;
    public static final ModConfigSpec.ConfigValue<String> MARKER;
    public static final ModConfigSpec.BooleanValue BLOCK_PLAYER_NAMES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        MIN_LENGTH = b.comment("Shortest nickname allowed (color codes don't count).")
                .defineInRange("min_length", 3, 1, 32);
        MAX_LENGTH = b.comment("Longest nickname allowed (color codes don't count).")
                .defineInRange("max_length", 16, 1, 32);
        ALLOWED = b.comment("Characters allowed in nicknames.")
                .define("allowed_characters", "A-Za-z0-9_");
        MARKER = b.comment("Shown in front of every nickname so players know it isn't a real name. Leave empty for none.")
                .define("marker", "~");
        BLOCK_PLAYER_NAMES = b.comment("Stop nicknames that match another player's real name or nickname.")
                .define("block_player_names", true);

        SPEC = b.build();
    }

    private NickConfig() {}
}

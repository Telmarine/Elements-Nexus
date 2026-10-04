package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/elements_nexus/broadcast.toml */
public final class BroadcastConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> CHAT_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> TITLE_COLOR;
    public static final ModConfigSpec.ConfigValue<String> SUBTITLE_COLOR;
    public static final ModConfigSpec.IntValue FADE_IN;
    public static final ModConfigSpec.IntValue STAY;
    public static final ModConfigSpec.IntValue FADE_OUT;
    public static final ModConfigSpec.ConfigValue<String> SOUND;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        CHAT_FORMAT = b.comment("How /broadcast looks in chat. {message} = the text. & colors work.")
                .define("chat_format", "&6&l[Broadcast] &e{message}");

        b.comment("/broadcast screen <title> | <subtitle>").push("screen");
        TITLE_COLOR = b.comment("Starting color of the big text.").define("title_color", "&6&l");
        SUBTITLE_COLOR = b.comment("Starting color of the small text.").define("subtitle_color", "&e");
        FADE_IN = b.comment("Seconds to fade in.").defineInRange("fade_in_seconds", 1, 0, 10);
        STAY = b.comment("Seconds the text stays.").defineInRange("stay_seconds", 5, 1, 60);
        FADE_OUT = b.comment("Seconds to fade out.").defineInRange("fade_out_seconds", 1, 0, 10);
        b.pop();

        SOUND = b.comment("Sound played with every broadcast. Leave empty for none.")
                .define("sound", "minecraft:block.note_block.pling");

        SPEC = b.build();
    }

    private BroadcastConfig() {}
}

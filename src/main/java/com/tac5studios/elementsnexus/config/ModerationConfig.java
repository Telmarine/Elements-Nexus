package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/elements_nexus/moderation.toml */
public final class ModerationConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue JAIL_RADIUS;
    public static final ModConfigSpec.BooleanValue JAIL_BLOCKS_CHAT;
    public static final ModConfigSpec.ConfigValue<String> JAIL_NOTICE;
    public static final ModConfigSpec.ConfigValue<String> JAIL_TITLE;
    public static final ModConfigSpec.ConfigValue<String> JAIL_SUBTITLE;
    public static final ModConfigSpec.IntValue LOG_MAX_MB;
    public static final ModConfigSpec.IntValue LOG_KEEP;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        JAIL_RADIUS = b.comment("How many blocks a jailed player can move from the jail spot before being pulled back.")
                .defineInRange("jail_radius", 5, 1, 64);
        JAIL_BLOCKS_CHAT = b.comment("Jailed players can't use public chat, and /msg only reaches staff.")
                .define("jail_blocks_chat", true);
        JAIL_NOTICE = b.comment("Shown in chat when a jailed player tries to chat.")
                .define("jail_notice", "&cYou are in jail. Message a staff member with /msg to find out why.");
        JAIL_TITLE = b.comment("Big text in the middle of the jailed player's screen. Only they see it.")
                .define("jail_title", "&cYou are in jail");
        JAIL_SUBTITLE = b.comment("Smaller line under it. Tell them how to reach staff (in game or Discord).")
                .define("jail_subtitle", "&7Message a staff member with /msg to find out why");

        b.comment("The staff log (world/elements_nexus/staff_log.jsonl).").push("staff_log");
        LOG_MAX_MB = b.comment("When the log reaches this size (MB), it is packed into the archive folder and a new one starts.")
                .defineInRange("max_size_mb", 5, 1, 1024);
        LOG_KEEP = b.comment("How many packed logs to keep. Older ones are deleted. 0 = keep them all.")
                .defineInRange("keep_archives", 0, 0, 10000);
        b.pop();

        SPEC = b.build();
    }

    private ModerationConfig() {}
}

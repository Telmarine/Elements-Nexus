package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

/** config/elements_nexus/tablist.toml - how the Tab list and nametags look. */
public final class TablistConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> HEADER;
    public static final ModConfigSpec.ConfigValue<String> ENTRY;
    public static final ModConfigSpec.BooleanValue RANK_SMALL_CAPS;
    public static final ModConfigSpec.ConfigValue<String> AFK_ENTRY;
    public static final ModConfigSpec.ConfigValue<String> SORTING;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOOTER;
    public static final ModConfigSpec.IntValue REFRESH;
    public static final ModConfigSpec.ConfigValue<String> NAMETAG;

    private static final String BAR = "<gradient:#3A3A3A:#FFD700:#3A3A3A>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>";

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment(
                "TAB LIST - what players see when they hold the Tab key.",
                "Colors: &a, &6 ... or hex like &#FFD700",
                "Styles: <smallcaps>Text</smallcaps>",
                "        <gradient:#FFD700:#FF4D2E>Text</gradient>   (colors fade across the text)",
                "        <shimmer:#FFD700:#FF4D2E>Text</shimmer>     (same, but the colors move)",
                "Placeholders: {player} {rank} {online}"
        ).push("header");
        HEADER = b.comment("Lines shown ABOVE the player list. One entry = one line.")
                .defineListAllowEmpty("lines", () -> Arrays.asList(
                        BAR,
                        "&6✦ <shimmer:#FFD700:#FF9A1F:#FF4D2E><smallcaps>My Server</smallcaps></shimmer> &6✦",
                        "&7Welcome back, &f{player}",
                        BAR), () -> "", o -> o instanceof String);
        b.pop();

        b.push("player_entry");
        ENTRY = b.comment("How each player's row looks.", "Placeholders: {rank} {player}")
                .define("format", "{rank} &8┃ &f{player}");
        RANK_SMALL_CAPS = b.comment("Show rank names in small caps (ᴏᴡɴᴇʀ).")
                .define("rank_small_caps", true);
        AFK_ENTRY = b.comment("How an AFK player's row looks. Here {rank} has no color, so the whole row can be gray.")
                .define("afk_format", "&8{rank} ┃ {player} ᶻᶻ");
        b.pop();

        b.push("sorting");
        SORTING = b.comment(
                "Order of the player list, top to bottom.",
                "by_rank = highest rank priority first, then by name",
                "by_name = by name only"
        ).defineInList("mode", "by_rank", Arrays.asList("by_rank", "by_name"));
        b.pop();

        b.push("footer");
        FOOTER = b.comment("Lines shown BELOW the player list.", "{online} = number of players online")
                .defineListAllowEmpty("lines", () -> Arrays.asList(
                        BAR,
                        "&8• &7Online &f{online} &8•"), () -> "", o -> o instanceof String);
        b.pop();

        b.push("refresh");
        REFRESH = b.comment("How often the tab list updates, in seconds. 1 = smooth shimmer.")
                .defineInRange("seconds", 1, 1, 60);
        b.pop();

        b.push("nametag");
        NAMETAG = b.comment("What shows above a player's head.", "Placeholders: {rank} {player}")
                .define("format", "{rank} &8┃ &f{player}");
        b.pop();

        SPEC = b.build();
    }

    private TablistConfig() {}
}

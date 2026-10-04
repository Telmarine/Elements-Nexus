package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

/** config/elements_nexus/sidepanel.toml */
public final class SidePanelConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue DEFAULT_ON;
    public static final ModConfigSpec.ConfigValue<String> TITLE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LINES;
    public static final ModConfigSpec.ConfigValue<String> NO_TITLE;
    public static final ModConfigSpec.ConfigValue<String> SEPARATOR;
    public static final ModConfigSpec.IntValue REFRESH;
    public static final ModConfigSpec.ConfigValue<String> BALANCE_SOURCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COINS;
    public static final ModConfigSpec.BooleanValue SHOW_EMPTY_COINS;
    public static final ModConfigSpec.ConfigValue<String> WILDERNESS;
    public static final ModConfigSpec.ConfigValue<String> CLAIMED;
    public static final ModConfigSpec.ConfigValue<String> SERVER_LAND;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        DEFAULT_ON = b.comment("Show the side panel to players who haven't turned it off with /sidepanel.")
                .define("default_on", true);

        b.comment("The panel on the right of the screen.").push("title");
        TITLE = b.comment("The line at the top. & colors work.").define("text", "&6&lMy Server");
        b.pop();

        b.comment("Lines in the panel, top to bottom.",
                "Available: {player} {name} {rank} {title} {balance} {location}",
                "Lines with {balance} or {location} are hidden when there is nothing to show.",
                "Use \"\" for an empty line.").push("lines");
        LINES = b.comment("One entry = one line. 15 lines at most.")
                .defineListAllowEmpty("lines", () -> Arrays.asList(
                        "",
                        "&7Name: &f{player}",
                        "&7Rank: {rank}",
                        "&7Balance: {balance}",
                        "&7Location: {location}",
                        ""), () -> "", o -> o instanceof String);
        SEPARATOR = b.comment("Put between info lines. Leave empty for none.").define("separator", "&8--------------");
        NO_TITLE = b.comment("Shown for {title} when the player has no title.").define("no_title", "&8none");
        b.pop();

        b.comment("Where {balance} comes from.").push("balance");
        BALANCE_SOURCE = b.comment(
                "auto     = a supported money mod if one is installed, otherwise coin items from the list below",
                "coins    = count the coin items below that the player carries (boxes and bags count too)",
                "monopoly = project:monopoly balance",
                "none     = hide the balance line").defineInList("source", "auto", Arrays.asList("auto", "coins", "monopoly", "none"));
        COINS = b.comment("Coin items. Format: \"item ID=color letter\". Shown as \"B: 12\" in that color.")
                .defineListAllowEmpty("coins", () -> Arrays.asList(
                        "aiycoin:coin_bronze=&#CD7F32B",
                        "aiycoin:coin_silver=&#C0C0C0S",
                        "aiycoin:coin_gold=&#FFD700G"), () -> "", o -> o instanceof String);
        SHOW_EMPTY_COINS = b.comment("Show coins the player has none of (as 0).").define("show_empty", true);
        b.pop();

        b.comment("{location} - who owns the land you stand on.",
                "Works with Open Parties and Claims and FTB Chunks. Hidden if neither is installed.").push("location");
        WILDERNESS = b.comment("Land nobody has claimed.").define("wilderness", "&2Wilderness");
        CLAIMED = b.comment("A player's land. {claim} = the claim's name, or the owner's name.").define("claimed", "&e{claim}");
        SERVER_LAND = b.comment("Server land. {claim} = the claim's name.").define("server", "&b{claim}");
        b.pop();

        b.push("refresh");
        REFRESH = b.comment("Seconds between updates.").defineInRange("seconds", 2, 1, 60);
        b.pop();

        SPEC = b.build();
    }

    private SidePanelConfig() {}
}

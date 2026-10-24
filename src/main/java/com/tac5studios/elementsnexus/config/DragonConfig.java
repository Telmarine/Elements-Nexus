package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * config/elements_nexus/dragon.toml - Ender Dragon auto respawn. Switch: features.toml [dragon] auto_respawn.
 * Every value here applies when the file is saved or with /nexus reload.
 */
public final class DragonConfig {

    public static final ModConfigSpec SPEC;

    // [respawn]
    public static final ModConfigSpec.IntValue RESPAWN_MINUTES;
    public static final ModConfigSpec.ConfigValue<String> RESPAWN_MESSAGE;
    public static final ModConfigSpec.IntValue PLAYER_RADIUS;
    public static final ModConfigSpec.IntValue RETRY_SECONDS;
    public static final ModConfigSpec.IntValue MAX_RETRIES;
    public static final ModConfigSpec.BooleanValue FIX_PORTAL_LOCATION;
    public static final ModConfigSpec.ConfigValue<String> FAILED_MESSAGE;

    // [portal]
    public static final ModConfigSpec.ConfigValue<String> PORTAL_MODE;
    public static final ModConfigSpec.IntValue PORTAL_X;
    public static final ModConfigSpec.IntValue PORTAL_Y;
    public static final ModConfigSpec.IntValue PORTAL_Z;
    public static final ModConfigSpec.IntValue SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue SEARCH_MIN_Y;
    public static final ModConfigSpec.IntValue SEARCH_MAX_Y;

    // [crystals]
    public static final ModConfigSpec.ConfigValue<String> PLACEMENT;
    public static final ModConfigSpec.IntValue DISTANCE;
    public static final ModConfigSpec.IntValue HEIGHT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> POSITIONS;
    public static final ModConfigSpec.BooleanValue REMOVE_MISPLACED;
    public static final ModConfigSpec.IntValue CLEANUP_RADIUS;
    public static final ModConfigSpec.BooleanValue PLACE_MISSING_ONLY;
    public static final ModConfigSpec.BooleanValue SHOW_BOTTOM;

    private static final List<String> MODES = List.of("auto", "vanilla", "fixed");
    private static final List<String> PLACEMENTS = List.of("bedrock", "offset", "list");

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("respawn");
        RESPAWN_MINUTES = b.comment(
                "Minutes after the Ender Dragon dies before it respawns.",
                "The respawn starts once the time is up and a player is near the exit portal,",
                "so the arena is loaded and the vanilla respawn (crystals, pillars, new gateway) can play out.",
                "The timer is saved, so a restart doesn't reset it."
        ).defineInRange("minutes", 30, 1, 10080);
        RESPAWN_MESSAGE = b.comment(
                "Message sent to everyone when the respawn starts. Supports & color codes. Empty = no message."
        ).define("message", "&5The Ender Dragon stirs... it returns to The End!");
        PLAYER_RADIUS = b.comment(
                "A player must be within this many blocks of the exit portal for the respawn to start."
        ).defineInRange("require_player_radius", 128, 16, 1024);
        RETRY_SECONDS = b.comment(
                "If the respawn hasn't started this many seconds after the crystals were placed, try again."
        ).defineInRange("retry_seconds", 60, 10, 3600);
        MAX_RETRIES = b.comment(
                "Tries before staff are told the respawn failed (in game, and on Discord when it is on).",
                "After that the timer starts again from the beginning."
        ).defineInRange("max_retries", 3, 1, 20);
        FIX_PORTAL_LOCATION = b.comment(
                "When the exit portal is found somewhere other than where the dragon fight has it saved,",
                "save the found spot, so hand-placed respawns line up as well."
        ).define("fix_portal_location", true);
        FAILED_MESSAGE = b.comment(
                "Staff alert when the respawn keeps failing. Available: {x} {y} {z} {tries}. Empty = no alert."
        ).define("failed_message", "&cThe Ender Dragon respawn failed {tries} times. Exit portal used: {x} {y} {z}. Check /nexus dragon status.");
        b.pop();

        b.comment("Where the exit portal is.").push("portal");
        PORTAL_MODE = b.comment(
                "auto    = find the real exit portal: the bedrock pillar near the dragon fight's centre (default).",
                "vanilla = use the location the dragon fight has saved.",
                "fixed   = use x, y, z below: the portal centre, at the height of the outer bedrock ring."
        ).define("mode", "auto", o -> o instanceof String s && MODES.contains(s.toLowerCase(Locale.ROOT)));
        PORTAL_X = b.comment("Portal centre for fixed mode.").defineInRange("x", 0, -30_000_000, 30_000_000);
        PORTAL_Y = b.defineInRange("y", 64, -2048, 2048);
        PORTAL_Z = b.defineInRange("z", 0, -30_000_000, 30_000_000);
        SEARCH_RADIUS = b.comment("Auto mode: how far from the dragon fight's centre to look for the pillar.")
                .defineInRange("search_radius", 16, 0, 64);
        SEARCH_MIN_Y = b.comment("Auto mode: lowest and highest Y to search (kept inside the world's limits).")
                .defineInRange("search_min_y", -64, -2048, 2048);
        SEARCH_MAX_Y = b.defineInRange("search_max_y", 320, -2048, 2048);
        b.pop();

        b.comment("Where the End Crystals go.").push("crystals");
        PLACEMENT = b.comment(
                "bedrock = on top of the outer bedrock block on each of the 4 sides of the portal (default).",
                "offset  = 'distance' blocks out from the centre on each side, 'height' blocks above the outer bedrock ring.",
                "list    = the exact spots in 'positions'.",
                "Portal mode 'vanilla' always uses the vanilla spots."
        ).define("placement", "bedrock", o -> o instanceof String s && PLACEMENTS.contains(s.toLowerCase(Locale.ROOT)));
        DISTANCE = b.comment("Offset placement: blocks from the portal centre.").defineInRange("distance", 3, 1, 16);
        HEIGHT = b.comment("Offset placement: blocks above the outer bedrock ring (1 = standing on it).").defineInRange("height", 1, -16, 16);
        POSITIONS = b.comment(
                "List placement: \"x y z\" for each crystal (the block the crystal stands in), e.g.",
                "[\"3 65 0\", \"-3 65 0\", \"0 65 3\", \"0 65 -3\"]"
        ).defineListAllowEmpty("positions", ArrayList::new, () -> "0 64 0", o -> o instanceof String);
        REMOVE_MISPLACED = b.comment("Remove End Crystals near the portal that aren't on one of the spots, before placing new ones.")
                .define("remove_misplaced", true);
        CLEANUP_RADIUS = b.comment("How far from the portal centre misplaced crystals are removed.")
                .defineInRange("cleanup_radius", 8, 1, 32);
        PLACE_MISSING_ONLY = b.comment("Leave spots that already hold a crystal alone.")
                .define("place_missing_only", true);
        SHOW_BOTTOM = b.comment("Show the bedrock plate under the crystals Nexus places.")
                .define("show_bottom", false);
        b.pop();

        SPEC = b.build();
    }

    private DragonConfig() {}

    public static String mode() {
        return PORTAL_MODE.get().toLowerCase(Locale.ROOT);
    }

    public static String placement() {
        return PLACEMENT.get().toLowerCase(Locale.ROOT);
    }
}

package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

/** config/elements_nexus/waystones.toml - waystone rules per dimension. Turn on in features.toml [waystones]. */
public final class WaystonesConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MODS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DENY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOW_TYPES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        MODS = b.comment("Blocks that count as waystones: a mod id (every block from that mod) or a block id.",
                        "Placing rules work for all of them. Activating and teleport rules need the Waystones mod.")
                .defineListAllowEmpty("waystone_blocks", () -> Arrays.asList("waystones"), () -> "", o -> o instanceof String);

        DENY = b.comment("What is NOT allowed in a dimension. Format: \"dimension id=words\"",
                        "Words: place   = placing waystones",
                        "       activate = activating a waystone for the first time",
                        "       to       = teleporting to a waystone in this dimension",
                        "       from     = teleporting away from this dimension with a waystone",
                        "Example: [\"minecraft:the_nether=place,activate\", \"mymod:mining_world=place,activate,to\"]",
                        "Staff with nexus.waystones.bypass ignore these rules.")
                .defineListAllowEmpty("deny", java.util.ArrayList::new, () -> "", o -> o instanceof String);

        ALLOW_TYPES = b.comment("Waystone types the teleport rules (to / from) never block.",
                        "A teleport is let through only when it starts AND ends at one of these types.",
                        "Warp stones, scrolls and the inventory button don't start at a waystone, so they stay blocked.",
                        "Placing and activating rules still apply to these types.",
                        "Example: [\"waystones:warp_plate\"]")
                .defineListAllowEmpty("allow_types", java.util.ArrayList::new, () -> "", o -> o instanceof String);

        SPEC = b.build();
    }

    private WaystonesConfig() {}
}

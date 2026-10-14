package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * config/elements_nexus/limits.toml - per-chunk entity limits. Turn on in features.toml [entity_limits].
 * Turned off on its own when Elements: Claims is installed (Claims' limits.toml is used instead).
 */
public final class LimitsConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue PETS, PASSIVE, MONSTERS, VILLAGERS;
    public static final ModConfigSpec.IntValue ARMOR_STANDS, ITEM_FRAMES, PAINTINGS, MINECARTS, BOATS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM;
    public static final ModConfigSpec.ConfigValue<String> MSG_MOBS, MSG_PLACED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Mobs per chunk. -1 = no limit.",
                "Over a limit, breeding, spawn eggs and spawners stop adding mobs of that kind. Nothing already there is removed.",
                "Easy NPC entities never count.").push("mobs");
        PETS = b.comment("Tamed animals.").defineInRange("pets", -1, -1, 10000);
        PASSIVE = b.comment("Animals and other friendly mobs.").defineInRange("passive", 40, -1, 10000);
        MONSTERS = b.comment("Hostile mobs (spawners and spawn eggs only; natural spawning uses vanilla's mob cap).").defineInRange("monsters", -1, -1, 10000);
        VILLAGERS = b.comment("Villagers and wandering traders.").defineInRange("villagers", 16, -1, 10000);
        b.pop();

        b.comment("Entities players place, per chunk. -1 = no limit.").push("placed");
        ARMOR_STANDS = b.defineInRange("armor_stands", 16, -1, 10000);
        ITEM_FRAMES = b.comment("Item frames and glow item frames together.").defineInRange("item_frames", 64, -1, 10000);
        PAINTINGS = b.defineInRange("paintings", 32, -1, 10000);
        MINECARTS = b.comment("All minecart types together.").defineInRange("minecarts", 16, -1, 10000);
        BOATS = b.comment("All boat types together, chest boats included.").defineInRange("boats", 16, -1, 10000);
        CUSTOM = b.comment("Your own limits for any entity id or entity tag, placed with an item or a spawn egg.",
                        "Format: \"id or #tag=max\". Example: [\"minecraft:allay=4\", \"#minecraft:raiders=0\"]")
                .defineListAllowEmpty("custom", java.util.ArrayList::new, () -> "", o -> o instanceof String);
        b.pop();

        b.comment("Action bar messages. {limit} = what hit the limit, {max} = the number.").push("messages");
        MSG_MOBS = b.define("mobs", "&cThis chunk already has {max} {limit}.");
        MSG_PLACED = b.define("placed", "&cThis chunk already has {max} {limit}.");
        b.pop();

        SPEC = b.build();
    }

    private LimitsConfig() {}
}

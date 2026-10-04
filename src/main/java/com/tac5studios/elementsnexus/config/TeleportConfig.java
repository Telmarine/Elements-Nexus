package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/elements_nexus/teleport.toml - homes and teleport safety. */
public final class TeleportConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue DEFAULT_HOMES;
    public static final ModConfigSpec.IntValue WARMUP;
    public static final ModConfigSpec.IntValue COMBAT_LOCK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("homes");
        DEFAULT_HOMES = b.comment(
                "How many homes a player gets when their rank does not set a number.",
                "Give a rank more with: /rank group homes <rank> <number>"
        ).defineInRange("default_limit", 1, 0, 1000);
        b.pop();

        b.push("safety");
        WARMUP = b.comment("Seconds a player must stand still before a teleport. Moving or getting hurt cancels it. 0 = off.")
                .defineInRange("warmup_seconds", 3, 0, 60);
        COMBAT_LOCK = b.comment("Seconds after a fight with another player before teleports work again. 0 = off.")
                .defineInRange("combat_lock_seconds", 10, 0, 600);
        b.pop();

        SPEC = b.build();
    }

    private TeleportConfig() {}
}

package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/elements_nexus/toggles.toml - player PvP and phantom toggles. */
public final class TogglesConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue PVP_DEFAULT_ON;
    public static final ModConfigSpec.IntValue PVP_COOLDOWN;
    public static final ModConfigSpec.IntValue PVP_COMBAT;
    public static final ModConfigSpec.BooleanValue PHANTOMS_DEFAULT_ON;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("/pvp - players choose if other players can hurt them. Only damage is blocked,",
                "so right-click interactions between players (riding, trading, etc.) still work.").push("pvp");
        PVP_DEFAULT_ON = b.comment("PvP is on for players who haven't used /pvp yet.").define("default_on", true);
        PVP_COOLDOWN = b.comment("Seconds a player must wait between /pvp changes. 0 = no wait.")
                .defineInRange("cooldown_seconds", 30, 0, 3600);
        PVP_COMBAT = b.comment("Seconds after hitting or being hit by a player before /pvp works again. 0 = off.")
                .defineInRange("combat_seconds", 15, 0, 600);
        b.pop();

        b.comment("/phantoms - players choose if phantoms spawn for them when they skip sleep.").push("phantoms");
        PHANTOMS_DEFAULT_ON = b.comment("Phantoms spawn for players who haven't used /phantoms yet.")
                .define("default_on", true);
        b.pop();

        SPEC = b.build();
    }

    private TogglesConfig() {}
}

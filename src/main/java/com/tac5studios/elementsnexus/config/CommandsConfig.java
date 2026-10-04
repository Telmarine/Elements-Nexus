package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

/** config/elements_nexus/commands.toml */
public final class CommandsConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> STAFF_ONLY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        STAFF_ONLY = b.comment(
                "Commands only staff can see and use (from any mod or vanilla).",
                "Staff = players with nexus.commands.restricted (OP level 2 by default).",
                "Changes need a server restart."
        ).defineListAllowEmpty("staff_only", () -> Arrays.asList("config", "neoforge", "trigger"),
                () -> "", o -> o instanceof String s && !s.isBlank());

        SPEC = b.build();
    }

    private CommandsConfig() {}
}

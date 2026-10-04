package com.tac5studios.elementsnexus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;

/** config/elements_nexus/storage.toml */
public final class StorageConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> BACKEND;
    public static final ModConfigSpec.ConfigValue<String> FOLDER;
    public static final ModConfigSpec.IntValue SAVE_INTERVAL;

    public static final ModConfigSpec.ConfigValue<String> MYSQL_HOST;
    public static final ModConfigSpec.IntValue MYSQL_PORT;
    public static final ModConfigSpec.ConfigValue<String> MYSQL_DATABASE;
    public static final ModConfigSpec.ConfigValue<String> MYSQL_USERNAME;
    public static final ModConfigSpec.ConfigValue<String> MYSQL_PASSWORD;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        BACKEND = b.comment(
                "Where Elements: Nexus saves its data.",
                "json   = plain files, lightest, best for most servers (default)",
                "yaml   = plain files, same as json but YAML format",
                "sqlite = one database file, good for big servers",
                "mysql  = external database, for networks sharing data between servers",
                "If the one you pick fails to start, Elements: Nexus uses json and logs a warning.",
                "This change needs a server restart."
        ).defineInList("backend", "json", Arrays.asList("json", "yaml", "sqlite", "mysql"));

        FOLDER = b.comment("Folder for json/yaml files and the sqlite file. Starts from the server folder.")
                .define("folder", "elements_nexus/store");

        SAVE_INTERVAL = b.comment("Seconds between saving changed data to disk.")
                .defineInRange("save_interval_seconds", 60, 5, 3600);

        b.comment("Only used when backend = \"mysql\".").push("mysql");
        MYSQL_HOST = b.comment("Database server address.").define("host", "localhost");
        MYSQL_PORT = b.comment("Database server port.").defineInRange("port", 3306, 1, 65535);
        MYSQL_DATABASE = b.comment("Database name.").define("database", "elements_nexus");
        MYSQL_USERNAME = b.comment("Database user.").define("username", "user");
        MYSQL_PASSWORD = b.comment("Database password.").define("password", "change_me");
        b.pop();

        SPEC = b.build();
    }

    private StorageConfig() {}
}

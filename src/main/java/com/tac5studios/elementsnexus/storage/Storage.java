package com.tac5studios.elementsnexus.storage;

import com.tac5studios.elementsnexus.ElementsNexus;
import com.tac5studios.elementsnexus.config.StorageConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.time.Instant;

/** Starts, runs and stops the data store for the server. */
public final class Storage {

    private static DataStore store;
    private static int ticks;

    private Storage() {}

    /** The live data store. Only valid while the server is running. */
    public static DataStore get() {
        if (store == null) throw new IllegalStateException("Elements: Nexus storage is not running.");
        return store;
    }

    public static void start() {
        Path folder = FMLPaths.GAMEDIR.get().resolve(StorageConfig.FOLDER.get()).normalize();
        String wanted = StorageConfig.BACKEND.get();

        StorageBackend backend;
        try {
            backend = create(wanted, folder);
            backend.open();
        } catch (Exception | LinkageError ex) { // LinkageError = a bundled driver failed to load
            ElementsNexus.LOGGER.warn("[Nexus] Storage '{}' could not start ({}). Using json instead.", wanted, ex.toString());
            backend = new JsonBackend(folder);
            try {
                backend.open();
            } catch (Exception fatal) {
                throw new RuntimeException("Elements: Nexus could not create its data folder: " + folder, fatal);
            }
        }

        store = new DataStore(backend);
        int loaded;
        try {
            loaded = store.loadAll();
        } catch (Exception ex) {
            ElementsNexus.LOGGER.error("[Nexus] Could not list saved data.", ex);
            loaded = 0;
        }
        store.put("meta", "last_start", Instant.now().toString());
        store.put("meta", "schema_version", 1);
        ticks = 0;

        ElementsNexus.LOGGER.info("[Nexus] Storage: {} at {} ({} data sets loaded).", backend.name(), folder, loaded);
    }

    /** Called every server tick. Saves on the timer from storage.toml. */
    public static void tick() {
        if (store == null) return;
        if (++ticks >= StorageConfig.SAVE_INTERVAL.get() * 20) {
            ticks = 0;
            store.flush(false);
        }
    }

    public static void stop() {
        if (store == null) return;
        store.close();
        store = null;
        ElementsNexus.LOGGER.info("[Nexus] Storage saved and closed.");
    }

    /** The storage folder from storage.toml. */
    public static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve(StorageConfig.FOLDER.get()).normalize();
    }

    static StorageBackend create(String name, Path folder) {
        return switch (name) {
            case "json" -> new JsonBackend(folder);
            case "yaml" -> new YamlBackend(folder);
            case "sqlite" -> new SqliteBackend(folder);
            case "mysql" -> new MysqlBackend();
            default -> throw new IllegalArgumentException("unknown backend '" + name + "'");
        };
    }
}

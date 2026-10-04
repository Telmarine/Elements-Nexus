package com.tac5studios.elementsnexus.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.tac5studios.elementsnexus.ElementsNexus;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand-written TOML file in config/elements_nexus/.
 * The first time, the default file (with all its comments) is copied out of the mod jar.
 * After that the server owner's copy is read and never rewritten.
 */
public class TomlFile {

    private final String name;
    private Config data = Config.inMemory();

    public TomlFile(String name) {
        this.name = name;
    }

    public Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(ElementsNexus.MOD_ID).resolve(name);
    }

    /** Copy the default if missing, then read the file. Returns false if it could not be read. */
    public boolean load() {
        Path p = path();
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                try (InputStream in = ElementsNexus.class.getResourceAsStream("/defaults/" + name)) {
                    if (in == null) throw new IllegalStateException("default " + name + " missing from the jar");
                    Files.copy(in, p);
                }
            }
            try (CommentedFileConfig cfg = CommentedFileConfig.builder(p).build()) {
                cfg.load();
                data = Config.copy(cfg);
            }
            return true;
        } catch (Exception e) {
            ElementsNexus.LOGGER.error("[Nexus] Could not read {}. Using built-in defaults.", p, e);
            data = Config.inMemory();
            return false;
        }
    }

    public String str(String key, String def) {
        Object v = data.get(key);
        return v instanceof String s ? s : def;
    }

    public boolean bool(String key, boolean def) {
        Object v = data.get(key);
        return v instanceof Boolean b ? b : def;
    }

    public int num(String key, int def) {
        Object v = data.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    /** A list of plain strings. */
    public List<String> strings(String key) {
        List<String> out = new ArrayList<>();
        if (data.get(key) instanceof List<?> l) for (Object o : l) if (o instanceof String s) out.add(s);
        return out;
    }

    /** A list of inline tables, e.g. [{ word = "x", match = "root" }]. */
    public List<Config> tables(String key) {
        List<Config> out = new ArrayList<>();
        if (data.get(key) instanceof List<?> l) for (Object o : l) if (o instanceof Config c) out.add(c);
        return out;
    }
}

package com.tac5studios.elementsnexus.migrate;

import com.tac5studios.elementsnexus.config.Features;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every source Nexus can import from. */
public final class Migrations {

    public interface Source {
        /** Name used in the command and in features.toml (migration.<id>). */
        String id();
        String label();
        /** True if this server has that mod's data. */
        boolean found(MinecraftServer server);
        void run(Importer im) throws Exception;
    }

    private static final Map<String, Source> SOURCES = new LinkedHashMap<>();

    static {
        add(new NeoEssentialsSource());
        add(new FtbRanksSource());
        add(new FtbEssentialsSource());
        add(new LuckPermsSource());
        add(new TabSource());
    }

    private static void add(Source s) {
        SOURCES.put(s.id(), s);
    }

    private Migrations() {}

    /** Sources that are switched on in features.toml. */
    public static List<Source> enabled() {
        List<Source> out = new ArrayList<>();
        for (Source s : SOURCES.values()) if (Features.on("migration", s.id())) out.add(s);
        return out;
    }

    public static Source get(String id) {
        Source s = SOURCES.get(id);
        return s != null && Features.on("migration", id) ? s : null;
    }
}

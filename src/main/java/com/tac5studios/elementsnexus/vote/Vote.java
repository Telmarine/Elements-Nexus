package com.tac5studios.elementsnexus.vote;

import com.tac5studios.elementsnexus.config.TomlFile;

import java.util.List;

/** The vote sites from vote.toml, shown by /vote. */
public final class Vote {

    private static final TomlFile FILE = new TomlFile("vote.toml");

    private Vote() {}

    public static void load() {
        FILE.load();
    }

    public static String header() { return FILE.str("vote.header", "&6&l✦ Vote for the Server ✦"); }
    public static List<String> lines() { return FILE.strings("vote.lines"); }
    public static String footer() { return FILE.str("vote.footer", ""); }
}

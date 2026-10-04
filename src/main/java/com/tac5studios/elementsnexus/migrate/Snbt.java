package com.tac5studios.elementsnexus.migrate;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads FTB's .snbt files. */
final class Snbt {

    private Snbt() {}

    static CompoundTag read(Importer im, Path p) {
        if (!Files.isRegularFile(p)) return null;
        try {
            return TagParser.parseTag(Files.readString(p, StandardCharsets.UTF_8));
        } catch (Exception e) {
            im.note("Could not read " + p.getFileName() + ": " + e.getMessage());
            return null;
        }
    }
}

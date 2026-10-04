package com.tac5studios.elementsnexus.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tac5studios.elementsnexus.ElementsNexus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** One .json file per collection: <folder>/<collection>.json */
public class JsonBackend implements StorageBackend {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path folder;

    public JsonBackend(Path folder) {
        this.folder = folder;
    }

    @Override
    public String name() {
        return "json";
    }

    @Override
    public void open() throws IOException {
        Files.createDirectories(folder);
    }

    @Override
    public Set<String> collections() throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .forEach(n -> names.add(n.substring(0, n.length() - 5)));
        }
        return names;
    }

    @Override
    public Map<String, JsonElement> load(String collection) throws IOException {
        Path file = file(collection);
        Map<String, JsonElement> out = new LinkedHashMap<>();
        if (!Files.exists(file)) return out;

        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (text.isBlank()) return out;

        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) out.put(e.getKey(), e.getValue());
        } catch (RuntimeException broken) {
            // Keep the broken file so nothing is lost, then start this collection empty.
            Path backup = folder.resolve(collection + ".json.broken-" + System.currentTimeMillis());
            Files.copy(file, backup);
            ElementsNexus.LOGGER.error("[Nexus] {} could not be read. A copy was saved as {}. Starting this data empty.",
                    file.getFileName(), backup.getFileName(), broken);
        }
        return out;
    }

    @Override
    public void save(String collection, Map<String, JsonElement> all, Set<String> changed, Set<String> removed) throws IOException {
        JsonObject root = new JsonObject();
        all.forEach(root::add);

        Path file = file(collection);
        Path tmp = folder.resolve(collection + ".json.tmp");
        Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void close() {
        // Nothing to close for plain files.
    }

    private Path file(String collection) {
        return folder.resolve(collection + ".json");
    }
}

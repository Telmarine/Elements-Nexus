package com.tac5studios.elementsnexus.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tac5studios.elementsnexus.ElementsNexus;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** One .yml file per collection: <folder>/<collection>.yml */
public class YamlBackend implements StorageBackend {

    private final Path folder;
    private Yaml yaml;

    public YamlBackend(Path folder) {
        this.folder = folder;
    }

    @Override
    public String name() {
        return "yaml";
    }

    @Override
    public void open() throws IOException {
        DumperOptions dump = new DumperOptions();
        dump.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dump.setIndent(2);
        LoaderOptions load = new LoaderOptions();
        load.setCodePointLimit(Integer.MAX_VALUE); // the default (3 MB) would treat big data files as broken
        yaml = new Yaml(new SafeConstructor(load), new Representer(dump), dump, load);
        Files.createDirectories(folder);
    }

    @Override
    public Set<String> collections() throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".yml"))
                    .forEach(n -> names.add(n.substring(0, n.length() - 4)));
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
            Object root = yaml.load(text);
            if (root instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) out.put(String.valueOf(e.getKey()), toJson(e.getValue()));
            }
        } catch (RuntimeException broken) {
            Path backup = folder.resolve(collection + ".yml.broken-" + System.currentTimeMillis());
            Files.copy(file, backup);
            ElementsNexus.LOGGER.error("[Nexus] {} could not be read. A copy was saved as {}. Starting this data empty.",
                    file.getFileName(), backup.getFileName(), broken);
        }
        return out;
    }

    @Override
    public void save(String collection, Map<String, JsonElement> all, Set<String> changed, Set<String> removed) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        all.forEach((k, v) -> root.put(k, fromJson(v)));

        Path file = file(collection);
        Path tmp = folder.resolve(collection + ".yml.tmp");
        Files.writeString(tmp, yaml.dump(root), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void close() {
    }

    private Path file(String collection) {
        return folder.resolve(collection + ".yml");
    }

    // ---------- JSON <-> YAML values ----------

    private static Object fromJson(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonObject()) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) m.put(x.getKey(), fromJson(x.getValue()));
            return m;
        }
        if (e.isJsonArray()) {
            List<Object> l = new ArrayList<>();
            for (JsonElement x : e.getAsJsonArray()) l.add(fromJson(x));
            return l;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isString()) return p.getAsString();
        BigDecimal n = p.getAsBigDecimal();
        try {
            return n.longValueExact(); // whole numbers stay whole (no "5.0")
        } catch (ArithmeticException notWhole) {
            return n.doubleValue();
        }
    }

    private static JsonElement toJson(Object o) {
        if (o == null) return JsonNull.INSTANCE;
        if (o instanceof Map<?, ?> m) {
            JsonObject obj = new JsonObject();
            for (Map.Entry<?, ?> x : m.entrySet()) obj.add(String.valueOf(x.getKey()), toJson(x.getValue()));
            return obj;
        }
        if (o instanceof List<?> l) {
            JsonArray arr = new JsonArray();
            for (Object x : l) arr.add(toJson(x));
            return arr;
        }
        if (o instanceof Boolean b) return new JsonPrimitive(b);
        if (o instanceof Number n) return new JsonPrimitive(n);
        return new JsonPrimitive(String.valueOf(o)); // strings, dates and anything else are kept as text
    }
}

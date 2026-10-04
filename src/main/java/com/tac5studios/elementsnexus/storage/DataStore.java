package com.tac5studios.elementsnexus.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.tac5studios.elementsnexus.ElementsNexus;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The shared data store. Everything is kept in memory and saved to the backend
 * in batches: changes are marked, then written on a timer and at shutdown.
 * Saving runs on its own thread so the server never waits on disk or database.
 */
public class DataStore {

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final StorageBackend backend;
    private final Map<String, Col> cols = new ConcurrentHashMap<>();
    private final ExecutorService saver = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Nexus-Saver");
        t.setDaemon(true);
        return t;
    });

    /** One collection in memory, plus what changed since the last save. */
    private static final class Col {
        final Map<String, JsonElement> data = new LinkedHashMap<>();
        final Set<String> changed = new HashSet<>();
        final Set<String> removed = new HashSet<>();
    }

    public DataStore(StorageBackend backend) {
        this.backend = backend;
    }

    public StorageBackend backend() {
        return backend;
    }

    // ---------- reading ----------

    public <T> T get(String collection, String key, Class<T> type) {
        JsonElement e = raw(collection, key);
        return e == null ? null : GSON.fromJson(e, type);
    }

    public <T> T get(String collection, String key, Type type) {
        JsonElement e = raw(collection, key);
        return e == null ? null : GSON.fromJson(e, type);
    }

    public JsonElement raw(String collection, String key) {
        Col c = col(collection);
        synchronized (c) {
            JsonElement e = c.data.get(key);
            return e == null ? null : e.deepCopy();
        }
    }

    public boolean has(String collection, String key) {
        Col c = col(collection);
        synchronized (c) {
            return c.data.containsKey(key);
        }
    }

    public List<String> keys(String collection) {
        Col c = col(collection);
        synchronized (c) {
            return new ArrayList<>(c.data.keySet());
        }
    }

    // ---------- writing ----------

    public void put(String collection, String key, Object value) {
        JsonElement e = value instanceof JsonElement je ? je.deepCopy() : GSON.toJsonTree(value);
        Col c = col(collection);
        synchronized (c) {
            c.data.put(key, e);
            c.changed.add(key);
            c.removed.remove(key);
        }
    }

    public void remove(String collection, String key) {
        Col c = col(collection);
        synchronized (c) {
            if (c.data.remove(key) != null) {
                c.removed.add(key);
                c.changed.remove(key);
            }
        }
    }

    /** A copy of every collection as it is in memory right now (includes unsaved changes). */
    public Map<String, Map<String, JsonElement>> snapshot() {
        Map<String, Map<String, JsonElement>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Col> e : cols.entrySet()) {
            Col c = e.getValue();
            Map<String, JsonElement> copy = new LinkedHashMap<>();
            synchronized (c) {
                c.data.forEach((k, v) -> copy.put(k, v.deepCopy()));
            }
            out.put(e.getKey(), copy);
        }
        return out;
    }

    // ---------- saving ----------

    /** Save every collection with changes. wait = true blocks until done (used at shutdown). */
    public void flush(boolean wait) {
        List<Future<?>> jobs = new ArrayList<>();
        for (Map.Entry<String, Col> entry : cols.entrySet()) {
            String name = entry.getKey();
            Col c = entry.getValue();
            Map<String, JsonElement> all;
            Set<String> changed;
            Set<String> removed;
            synchronized (c) {
                if (c.changed.isEmpty() && c.removed.isEmpty()) continue;
                all = new LinkedHashMap<>();
                c.data.forEach((k, v) -> all.put(k, v.deepCopy()));
                changed = new HashSet<>(c.changed);
                removed = new HashSet<>(c.removed);
                c.changed.clear();
                c.removed.clear();
            }
            jobs.add(saver.submit(() -> {
                try {
                    backend.save(name, all, changed, removed);
                } catch (Exception ex) {
                    ElementsNexus.LOGGER.error("[Nexus] Could not save '{}'. Will try again next save.", name, ex);
                    synchronized (c) { // put the changes back so they are retried
                        for (String k : changed) if (c.data.containsKey(k)) c.changed.add(k);
                        for (String k : removed) if (!c.data.containsKey(k)) c.removed.add(k);
                    }
                }
            }));
        }
        if (wait) {
            for (Future<?> f : jobs) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    ElementsNexus.LOGGER.error("[Nexus] A save did not finish in time.", ex);
                }
            }
        }
    }

    /** Save everything and close. Call once at shutdown. */
    public void close() {
        flush(true);
        saver.shutdown();
        try {
            saver.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        backend.close();
    }

    // ---------- loading ----------

    /** Load every collection the backend already has. */
    public int loadAll() throws Exception {
        Set<String> names = backend.collections();
        for (String n : names) col(n);
        return names.size();
    }

    private Col col(String collection) {
        return cols.computeIfAbsent(collection, name -> {
            Col c = new Col();
            try {
                c.data.putAll(backend.load(name));
            } catch (Exception ex) {
                ElementsNexus.LOGGER.error("[Nexus] Could not load '{}'.", name, ex);
            }
            return c;
        });
    }
}

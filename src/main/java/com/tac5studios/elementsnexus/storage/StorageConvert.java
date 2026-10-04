package com.tac5studios.elementsnexus.storage;

import com.google.gson.JsonElement;
import com.tac5studios.elementsnexus.ElementsNexus;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Copies everything in the running store into another backend. The running store is not changed. */
public final class StorageConvert {

    private static volatile boolean running;

    private StorageConvert() {}

    public static boolean running() {
        return running;
    }

    /**
     * Runs on its own thread. done gets null on success, or the error message.
     * The target ends up an exact copy: entries it had that the source doesn't are removed.
     */
    public static void start(String target, Consumer<String> done) {
        Map<String, Map<String, JsonElement>> snap = Storage.get().snapshot();
        running = true;
        Thread t = new Thread(() -> {
            StorageBackend to = null;
            try {
                to = Storage.create(target, Storage.folder());
                to.open();
                Set<String> all = new HashSet<>(snap.keySet());
                all.addAll(to.collections());
                for (String col : all) {
                    Map<String, JsonElement> data = snap.getOrDefault(col, Map.of());
                    Set<String> removed = new HashSet<>(to.load(col).keySet());
                    removed.removeAll(data.keySet());
                    to.save(col, data, new HashSet<>(data.keySet()), removed);
                }
                done.accept(null);
            } catch (Exception | LinkageError ex) {
                ElementsNexus.LOGGER.error("[Nexus] Storage convert to {} failed.", target, ex);
                done.accept(ex.toString());
            } finally {
                if (to != null) to.close();
                running = false;
            }
        }, "Nexus-Convert");
        t.setDaemon(true);
        t.start();
    }
}

package com.tac5studios.elementsnexus.storage;

import com.google.gson.JsonElement;

import java.util.Map;
import java.util.Set;

/**
 * A place to keep data. Data is split into collections (ranks, users, homes ...),
 * and each collection is a map of key -> JSON value.
 */
public interface StorageBackend {

    /** Short name for logs, e.g. "json". */
    String name();

    /** Connect or create files. Throw if it can't start. */
    void open() throws Exception;

    /** Names of every collection that has saved data. */
    Set<String> collections() throws Exception;

    /** Load every entry in one collection. Empty map if none. */
    Map<String, JsonElement> load(String collection) throws Exception;

    /**
     * Save changes to one collection.
     * @param all     every entry in the collection right now (file backends rewrite the whole file)
     * @param changed keys that were added or changed
     * @param removed keys that were deleted
     */
    void save(String collection, Map<String, JsonElement> all, Set<String> changed, Set<String> removed) throws Exception;

    /** Close connections and files. */
    void close();
}

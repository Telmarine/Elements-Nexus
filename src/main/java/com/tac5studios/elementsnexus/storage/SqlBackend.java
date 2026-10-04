package com.tac5studios.elementsnexus.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tac5studios.elementsnexus.ElementsNexus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Shared code for SQLite and MySQL.
 * Everything lives in one table: nexus_data (collection, data_key, data_value).
 */
public abstract class SqlBackend implements StorageBackend {

    protected static final String TABLE = "nexus_data";

    private Connection connection;

    /** Open a new database connection. */
    protected abstract Connection connect() throws SQLException;

    /** SQL to create the table if it is missing. */
    protected abstract String createTableSql();

    /** SQL to insert a row, or update it if the key already exists. Params: collection, key, value. */
    protected abstract String upsertSql();

    @Override
    public synchronized void open() throws SQLException {
        connection = connect();
        try (Statement st = connection.createStatement()) {
            st.execute(createTableSql());
        }
    }

    @Override
    public synchronized Set<String> collections() throws SQLException {
        Set<String> names = new TreeSet<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT collection FROM " + TABLE)) {
            while (rs.next()) names.add(rs.getString(1));
        }
        return names;
    }

    @Override
    public synchronized Map<String, JsonElement> load(String collection) throws SQLException {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT data_key, data_value FROM " + TABLE + " WHERE collection = ?")) {
            ps.setString(1, collection);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString(1);
                    try {
                        out.put(key, JsonParser.parseString(rs.getString(2)));
                    } catch (RuntimeException bad) {
                        ElementsNexus.LOGGER.error("[Nexus] Skipped unreadable entry '{}' in '{}'.", key, collection, bad);
                    }
                }
            }
        }
        return out;
    }

    @Override
    public synchronized void save(String collection, Map<String, JsonElement> all, Set<String> changed, Set<String> removed) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false); // all changes for this collection save together, or not at all
        try {
            if (!changed.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement(upsertSql())) {
                    for (String key : changed) {
                        JsonElement v = all.get(key);
                        if (v == null) continue;
                        ps.setString(1, collection);
                        ps.setString(2, key);
                        ps.setString(3, v.toString());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            if (!removed.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM " + TABLE + " WHERE collection = ? AND data_key = ?")) {
                    for (String key : removed) {
                        ps.setString(1, collection);
                        ps.setString(2, key);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
    }

    /** The live connection. Reconnects if the database dropped it (MySQL does this when idle). */
    private Connection conn() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            close();
            connection = connect();
        }
        return connection;
    }
}

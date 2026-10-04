package com.tac5studios.elementsnexus.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/** One database file: <folder>/nexus.db */
public class SqliteBackend extends SqlBackend {

    private final Path file;

    public SqliteBackend(Path folder) {
        this.file = folder.resolve("nexus.db");
    }

    @Override
    public String name() {
        return "sqlite";
    }

    @Override
    public synchronized void open() throws SQLException {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new SQLException("Could not create folder " + file.getParent(), e);
        }
        super.open();
    }

    @Override
    protected Connection connect() throws SQLException {
        // Use the driver directly. The usual DriverManager lookup does not see bundled drivers.
        Connection c = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file.toAbsolutePath(), new Properties());
        if (c == null) throw new SQLException("SQLite driver refused the connection");
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL"); // safer and faster writes
        }
        return c;
    }

    @Override
    protected String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "collection TEXT NOT NULL, "
                + "data_key TEXT NOT NULL, "
                + "data_value TEXT NOT NULL, "
                + "PRIMARY KEY (collection, data_key))";
    }

    @Override
    protected String upsertSql() {
        return "INSERT INTO " + TABLE + " (collection, data_key, data_value) VALUES (?, ?, ?) "
                + "ON CONFLICT(collection, data_key) DO UPDATE SET data_value = excluded.data_value";
    }
}

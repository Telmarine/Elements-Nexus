package com.tac5studios.elementsnexus.storage;

import com.tac5studios.elementsnexus.config.StorageConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/** External MySQL or MariaDB database, set in storage.toml [mysql]. */
public class MysqlBackend extends SqlBackend {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    protected Connection connect() throws SQLException {
        String url = "jdbc:mariadb://" + StorageConfig.MYSQL_HOST.get() + ":" + StorageConfig.MYSQL_PORT.get()
                + "/" + StorageConfig.MYSQL_DATABASE.get();
        Properties p = new Properties();
        p.setProperty("user", StorageConfig.MYSQL_USERNAME.get());
        p.setProperty("password", StorageConfig.MYSQL_PASSWORD.get());
        p.setProperty("allowPublicKeyRetrieval", "true"); // needed for MySQL 8 default logins
        p.setProperty("connectTimeout", "5000");
        // Use the driver directly. The usual DriverManager lookup does not see bundled drivers.
        Connection c = new org.mariadb.jdbc.Driver().connect(url, p);
        if (c == null) throw new SQLException("MySQL driver refused the connection");
        return c;
    }

    @Override
    protected String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "collection VARCHAR(64) NOT NULL, "
                + "data_key VARCHAR(191) NOT NULL, "
                + "data_value MEDIUMTEXT NOT NULL, "
                + "PRIMARY KEY (collection, data_key)) "
                + "DEFAULT CHARSET = utf8mb4";
    }

    @Override
    protected String upsertSql() {
        return "INSERT INTO " + TABLE + " (collection, data_key, data_value) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE data_value = VALUES(data_value)";
    }
}

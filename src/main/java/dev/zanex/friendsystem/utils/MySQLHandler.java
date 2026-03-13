package dev.zanex.friendsystem.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public final class MySQLHandler {
    private final HikariConfig config;
    private HikariDataSource dataSource;

    public MySQLHandler(HikariConfig config) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        this.config = config;
    }

    public synchronized void connect() {
        if (dataSource != null && !dataSource.isClosed()) return;
        dataSource = new HikariDataSource(config);
    }

    public synchronized void close() {
        if (dataSource == null) return;
        try {
            dataSource.close();
        } finally {
            dataSource = null;
        }
    }

    public Connection getConnection() throws SQLException {
        HikariDataSource ds = this.dataSource;
        if (ds == null) throw new SQLException("MySQLHandler is not connected (dataSource == null)");
        return ds.getConnection();
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}


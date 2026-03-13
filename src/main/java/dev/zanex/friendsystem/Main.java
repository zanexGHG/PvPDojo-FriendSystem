package dev.zanex.friendsystem;

import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariConfig;
import dev.zanex.core.labels.LabelsJson;
import dev.zanex.core.labels.JsonUtils;
import dev.zanex.friendsystem.commands.FCommandManager;
import dev.zanex.friendsystem.commands.impl.FriendsCommand;
import dev.zanex.friendsystem.friends.FriendService;
import dev.zanex.friendsystem.friends.FriendsManager;
import dev.zanex.friendsystem.listener.FriendChatListener;
import dev.zanex.friendsystem.listener.JoinQuitListener;
import dev.zanex.friendsystem.utils.MySQLHandler;
import dev.zanex.friendsystem.utils.SchedulerHelper;
import lombok.Getter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public final class Main extends JavaPlugin {
    @Getter
    private static Main instance;
    @Getter
    private LabelsJson labelLoader;
    @Getter
    private MySQLHandler mysql;

    @Getter
    private FCommandManager commandManager;
    @Getter
    private FriendsManager friendManager;
    @Getter
    private FriendService friendService;

    @Override
    public void onEnable() {
        instance = this;

        commandManager = new FCommandManager(this);
        commandManager.getLocales().setDefaultLocale(Locale.ENGLISH);

        try {
            labelLoader = new LabelsJson(readResourceToString("labels.json"));
        } catch (Exception e) {
            getLogger().severe("Failed to load labels.json from plugin jar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);

            return;
        }

        registerACFMessages();

        try {
            String jsonString = readResourceToString("mysql.json");
            JsonObject jsonObject = JsonUtils.parseCompat(jsonString).getAsJsonObject();

            mysql = new MySQLHandler(buildHikariConfig(jsonObject));
            mysql.connect();

            execSchema();

            getLogger().info("§aSuccessfully initialized MySQL connection pool.");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize MySQL connection: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);

            return;
        }

        friendManager = new FriendsManager();
        friendService = new FriendService();

        new FriendsCommand();

        instance.getServer().getLogger().info(labelLoader.of("messages.server.onEnable"));

        registerListener(new JoinQuitListener());
        registerListener(new FriendChatListener());

        cleanupStaleRequests();
    }

    private void registerACFMessages() {
        String usage = labelLoader.of("commands.friends.usage");
        commandManager.getLocales().addMessage(Locale.ENGLISH, co.aikar.locales.MessageKey.of("acf-core.invalid_syntax"), usage);
        commandManager.getLocales().addMessage(Locale.ENGLISH, co.aikar.locales.MessageKey.of("acf-core.error_performing_command"), usage);
        commandManager.getLocales().addMessage(Locale.ENGLISH, co.aikar.locales.MessageKey.of("acf-core.permission_denied"), labelLoader.of("messages.server.noPermission"));
    }

    @Override
    public void onDisable() {
        if (labelLoader != null) {
            String msg = labelLoader.of("messages.server.onDisable");
            if (msg != null) getLogger().info(msg);
        }

        if (mysql != null && mysql.isConnected()) {
            mysql.close();
        }
    }

    private static HikariConfig buildHikariConfig(JsonObject mysqlJson) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" +
                getString(mysqlJson, "host", "localhost") + ":" +
                getInt(mysqlJson, "port", 3306) + "/" +
                getString(mysqlJson, "database", "minecraft") +
                "?useSSL=false&autoReconnect=true");
        config.setUsername(getString(mysqlJson, "username", getString(mysqlJson, "user", "root")));
        config.setPassword(getString(mysqlJson, "password", ""));
        config.setPoolName("FriendSystem-Hikari");

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.setLeakDetectionThreshold(2000);

        if (mysqlJson.has("pool") && mysqlJson.get("pool").isJsonObject()) {
            JsonObject pool = mysqlJson.getAsJsonObject("pool");
            if (pool.has("maximumPoolSize")) config.setMaximumPoolSize(getInt(pool, "maximumPoolSize", 10));
            if (pool.has("minimumIdle")) config.setMinimumIdle(getInt(pool, "minimumIdle", 2));
            if (pool.has("connectionTimeout")) config.setConnectionTimeout(getLong(pool, "connectionTimeout", 30000L));
            if (pool.has("maxLifetime")) config.setMaxLifetime(getLong(pool, "maxLifetime", 1800000L));
        }

        return config;
    }

    private static String getString(JsonObject obj, String key, String def) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignored) {
            return def;
        }
    }

    private static int getInt(JsonObject obj, String key, int def) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return def;
        }
    }

    private static long getLong(JsonObject obj, String key, long def) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsLong();
        } catch (Exception ignored) {
            return def;
        }
    }

    public void registerCommand(String command, CommandExecutor executor, TabCompleter... tabCompleter) {
        this.getCommand(command).setExecutor(executor);

        if (tabCompleter != null && tabCompleter.length > 0) {
            this.getCommand(command).setTabCompleter(tabCompleter[0]);
        }
    }

    public void registerListener(Listener listener) {
        this.getServer().getPluginManager().registerEvents(listener, this);
    }

    private String readResourceToString(String resourcePath) throws IOException {
        try (InputStream in = getResource(resourcePath)) {
            if (in == null) throw new IOException("Resource not found: " + resourcePath);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void execSchema() throws IOException, SQLException {
        String schema = readResourceToString("SCHEMA.sql");

        // Strip line comments and blank lines
        StringBuilder cleaned = new StringBuilder();
        for (String line : schema.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("--")) continue;
            cleaned.append(line).append('\n');
        }

        String[] statements = cleaned.toString().split(";");
        try (Connection connection = mysql.getConnection();
             Statement st = connection.createStatement()) {
            for (String raw : statements) {
                String sql = raw.trim();
                if (sql.isEmpty()) continue;
                try {
                    st.execute(sql);
                } catch (SQLException e) {
                    throw new SQLException("Failed executing schema statement: " + sql, e);
                }
            }
        }
    }

    private void cleanupStaleRequests() {
        SchedulerHelper.async(() -> {
            try (Connection connection = mysql.getConnection();
                 Statement st = connection.createStatement()) {

                st.executeUpdate(
                        "DELETE r FROM fs_requests r " +
                                "JOIN fs_relations rel ON (" +
                                "(rel.uuid_a = r.uuid_from AND rel.uuid_b = r.uuid_to) OR " +
                                "(rel.uuid_a = r.uuid_to AND rel.uuid_b = r.uuid_from)" +
                                ") " +
                                "WHERE rel.relation = 0"
                );
            } catch (Exception ignored) {
            }
        });
    }

    private String mapACFKeyToLabel(String acfKey) {
        if (acfKey == null) return null;

        if (acfKey.equals("acf-core.invalid_syntax")) {
            return "commands.friends.usage";
        }
        if (acfKey.equals("acf-core.error_performing_command")) {
            return "messages.friend.notFriends";
        }
        if (acfKey.equals("acf-core.permission_denied")) {
            return "messages.server.noPermission";
        }

        return null;
    }

}

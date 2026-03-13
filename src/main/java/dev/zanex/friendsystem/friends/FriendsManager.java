package dev.zanex.friendsystem.friends;

import dev.zanex.friendsystem.Main;
import lombok.Getter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class FriendsManager {
    @Getter
    private final Map<UUID, List<Relation>> playerCache = new HashMap<>();

    public FriendsManager() {
        List<UUID> online = new ArrayList<>();
        Main.getInstance().getServer().getOnlinePlayers().forEach(p -> online.add(p.getUniqueId()));

        if (online.isEmpty()) {
            return;
        }

        String placeholders = String.join(",", Collections.nCopies(online.size(), "?"));
        String sql = "SELECT uuid FROM fs_players WHERE uuid IN (" + placeholders + ")";
        try (Connection connection = Main.getInstance().getMysql().getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            for (int i = 0; i < online.size(); i++) {
                ps.setString(i + 1, online.get(i).toString());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));

                    try (PreparedStatement relPs = connection.prepareStatement(
                            "SELECT uuid_a, uuid_b, relation FROM fs_relations WHERE (uuid_a = ? OR uuid_b = ?) AND relation != 1"
                    )) {
                        relPs.setString(1, uuid.toString());
                        relPs.setString(2, uuid.toString());

                        List<Relation> relations = new ArrayList<>();
                        try (ResultSet relationsRs = relPs.executeQuery()) {
                            while (relationsRs.next()) {
                                relations.add(new Relation(
                                        UUID.fromString(relationsRs.getString("uuid_a")),
                                        UUID.fromString(relationsRs.getString("uuid_b")),
                                        Relation.ofType(relationsRs.getInt("relation"))
                                ));
                            }
                        }

                        playerCache.put(uuid, relations);
                    }
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendNotification(Relation relation, String message) {
        if (relation == null || message == null) {
            return;
        }

        if (Main.getInstance().getServer().getPlayer(relation.getUuidA()) != null) {
            Main.getInstance().getServer().getPlayer(relation.getUuidA()).sendMessage(message);
        }

        if (Main.getInstance().getServer().getPlayer(relation.getUuidB()) != null) {
            Main.getInstance().getServer().getPlayer(relation.getUuidB()).sendMessage(message);
        }
    }

    public void upsertPlayer(UUID uuid) {
        if (uuid == null) return;

        try (Connection connection = Main.getInstance().getMysql().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO fs_players (uuid, last_seen) VALUES (?, CURRENT_TIMESTAMP) " +
                             "ON DUPLICATE KEY UPDATE last_seen = CURRENT_TIMESTAMP"
             )) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addRelation(UUID uuidA, UUID uuidB, Relation.RelationType type) {
        if (uuidA == null || uuidB == null || type == null) return;

        // Keep deterministic ordering so all relations are unique regardless of input order.
        UUID first = uuidA;
        UUID second = uuidB;
        if (first.toString().compareTo(second.toString()) > 0) {
            first = uuidB;
            second = uuidA;
        }

        try (Connection connection = Main.getInstance().getMysql().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO fs_relations (uuid_a, uuid_b, relation, last_changed) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                             "ON DUPLICATE KEY UPDATE relation = VALUES(relation), last_changed = CURRENT_TIMESTAMP"
             )) {
            ps.setString(1, first.toString());
            ps.setString(2, second.toString());
            ps.setInt(3, type.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeRelation(UUID uuidA, UUID uuidB) {
        if (uuidA == null || uuidB == null) return;

        UUID first = uuidA;
        UUID second = uuidB;
        if (first.toString().compareTo(second.toString()) > 0) {
            first = uuidB;
            second = uuidA;
        }

        try (Connection connection = Main.getInstance().getMysql().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM fs_relations WHERE uuid_a = ? AND uuid_b = ?"
             )) {
            ps.setString(1, first.toString());
            ps.setString(2, second.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

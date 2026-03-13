package dev.zanex.friendsystem.friends;

import dev.zanex.friendsystem.Main;
import dev.zanex.friendsystem.utils.SchedulerHelper;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FriendService {

    public void upsertPlayer(UUID uuid) {
        SchedulerHelper.async(() -> Main.getInstance().getFriendManager().upsertPlayer(uuid));
    }

    public void sendRequest(Player from, UUID toUuid, String toName) {
        if(from == null || toUuid == null) {
            return;
        }

        UUID fromUuid = from.getUniqueId();
        if(fromUuid.equals(toUuid)) {
            from.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.self"));

            return;
        }

        SchedulerHelper.async(() -> {
            try(Connection connection = Main.getInstance().getMysql().getConnection()) {
                if(isBlockedEitherWay(connection, fromUuid, toUuid)) {
                    SchedulerHelper.sync(() -> from.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.cannotRequestBlocked")));
                    return;
                }

                if(isFriends(connection, fromUuid, toUuid)) {
                    SchedulerHelper.sync(() -> from.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.alreadyFriends")
                            .replace("%player%", toName)));
                    return;
                }

                if(hasRequest(connection, fromUuid, toUuid)) {
                    SchedulerHelper.sync(() -> from.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.alreadyRequested")
                            .replace("%player%", toName)));
                    return;
                }

                try(PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO fs_requests (uuid_from, uuid_to) VALUES (?, ?) ON DUPLICATE KEY UPDATE created_at = created_at"
                )) {
                    ps.setString(1, fromUuid.toString());
                    ps.setString(2, toUuid.toString());
                    ps.executeUpdate();
                }

                SchedulerHelper.sync(() -> {
                    from.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.requestSent")
                            .replace("%player%", toName));

                    Player to = Bukkit.getPlayer(toUuid);
                    if(to != null) {
                        to.spigot().sendMessage(requestComponent(from.getName()));
                    }
                });
            } catch(SQLException e) {
                SchedulerHelper.sync(() -> from.sendMessage("§c§l! §7Database error."));
            }
        });
    }

    public void accept(Player to, UUID fromUuid, String fromName) {
        if(to == null || fromUuid == null) {
            return;
        }

        UUID toUuid = to.getUniqueId();
        SchedulerHelper.async(() -> {
            try(Connection connection = Main.getInstance().getMysql().getConnection()) {
                if(isFriends(connection, fromUuid, toUuid)) {
                    SchedulerHelper.sync(() -> to.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.alreadyFriends")
                            .replace("%player%", fromName)));

                    return;
                }

                if(!hasRequest(connection, fromUuid, toUuid)) {
                    SchedulerHelper.sync(() -> to.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.requestNotFound")
                            .replace("%player%", fromName)));

                    return;
                }

                deleteRequest(connection, fromUuid, toUuid);
                deleteRequest(connection, toUuid, fromUuid);

                Main.getInstance().getFriendManager().addRelation(fromUuid, toUuid, Relation.RelationType.FRIEND);

                SchedulerHelper.sync(() -> {
                    String acceptedTo = Main.getInstance().getLabelLoader().of("messages.friend.requestAccepted")
                            .replace("%player%", fromName);

                    to.sendMessage(acceptedTo);

                    Player from = Bukkit.getPlayer(fromUuid);
                    if(from != null) {
                        String acceptedFrom = Main.getInstance().getLabelLoader().of("messages.friend.requestAccepted")
                                .replace("%player%", to.getName());

                        from.sendMessage(acceptedFrom);
                    }
                });
            } catch(SQLException e) {
                SchedulerHelper.sync(() -> to.sendMessage("§c§l! §7Database error."));
            }
        });
    }

    public void block(Player player, UUID targetUuid, String targetName) {
        if(player == null || targetUuid == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if(uuid.equals(targetUuid)) {
            player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.self"));

            return;
        }

        SchedulerHelper.async(() -> {
            try(Connection connection = Main.getInstance().getMysql().getConnection()) {
                if(isBlocked(connection, uuid, targetUuid)) {
                    SchedulerHelper.sync(() -> player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.alreadyBlocked")
                            .replace("%player%", targetName)));
                    return;
                }

                Main.getInstance().getFriendManager().addRelation(uuid, targetUuid, Relation.RelationType.BLOCKED);
                deleteRequest(connection, uuid, targetUuid);
                deleteRequest(connection, targetUuid, uuid);

                SchedulerHelper.sync(() -> player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.blocked")
                        .replace("%player%", targetName)));
            } catch(SQLException e) {
                SchedulerHelper.sync(() -> player.sendMessage("§c§l! §7Database error."));
            }
        });
    }

    public void unblock(Player player, UUID targetUuid, String targetName) {
        if(player == null || targetUuid == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        SchedulerHelper.async(() -> {
            try(Connection connection = Main.getInstance().getMysql().getConnection()) {
                if(!isBlocked(connection, uuid, targetUuid)) {
                    SchedulerHelper.sync(() -> player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.notBlocked")
                            .replace("%player%", targetName)));
                    return;
                }

                Main.getInstance().getFriendManager().removeRelation(uuid, targetUuid);
                SchedulerHelper.sync(() -> player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.unblocked")
                        .replace("%player%", targetName)));
            } catch(SQLException e) {
                SchedulerHelper.sync(() -> player.sendMessage("§c§l! §7Database error."));
            }
        });
    }

    public void sendPendingRequests(Player player) {
        if(player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        SchedulerHelper.async(() -> {
            List<String> fromNames = new ArrayList<>();

            try(Connection connection = Main.getInstance().getMysql().getConnection();
                PreparedStatement ps = connection.prepareStatement("SELECT uuid_from FROM fs_requests WHERE uuid_to = ?")) {
                ps.setString(1, uuid.toString());

                try(ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        UUID fromUuid = UUID.fromString(rs.getString("uuid_from"));
                        String name = Bukkit.getOfflinePlayer(fromUuid).getName();

                        fromNames.add(name == null ? fromUuid.toString() : name);
                    }
                }
            } catch(SQLException e) {
                return;
            }

            if(fromNames.isEmpty()) {
                return;
            }

            SchedulerHelper.sync(() -> fromNames.forEach(name -> player.spigot().sendMessage(requestComponent(name))));
        });
    }

    public boolean isFriendsCached(UUID a, UUID b) {
        if(a == null || b == null) {
            return false;
        }

        List<Relation> relations = Main.getInstance().getFriendManager().getPlayerCache().get(a);
        if(relations == null) {
            return false;
        }

        for(Relation r : relations) {
            if(r.getRelationType() != Relation.RelationType.FRIEND) {
                continue;
            }

            if(r.getUuidA().equals(a) && r.getUuidB().equals(b)) {
                return true;
            }

            if(r.getUuidA().equals(b) && r.getUuidB().equals(a)) {
                return true;
            }
        }

        return false;
    }

    private boolean isFriends(Connection connection, UUID a, UUID b) throws SQLException {
        UUID first = a;
        UUID second = b;
        if(first.toString().compareTo(second.toString()) > 0) {
            first = b;
            second = a;
        }

        try(PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM fs_relations WHERE uuid_a = ? AND uuid_b = ? AND relation = 0 LIMIT 1"
        )) {

            ps.setString(1, first.toString());
            ps.setString(2, second.toString());
            try(ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasRequest(Connection connection, UUID from, UUID to) throws SQLException {
        try(PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM fs_requests WHERE uuid_from = ? AND uuid_to = ? LIMIT 1"
        )) {

            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try(ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void deleteRequest(Connection connection, UUID from, UUID to) throws SQLException {
        try(PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM fs_requests WHERE uuid_from = ? AND uuid_to = ?"
        )) {

            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ps.executeUpdate();
        }
    }

    private TextComponent requestComponent(String fromName) {
        TextComponent component = new TextComponent(Main.getInstance().getLabelLoader().of("messages.friend.requestReceived")
                .replace("%player%", fromName));

        TextComponent accept = new TextComponent("§a[ACCEPT]");
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friend accept " + fromName));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§aClick to accept").create()));

        TextComponent deny = new TextComponent("§c[DENY]");
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friend deny " + fromName));
        deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§cClick to deny").create()));

        component.addExtra(" ");
        component.addExtra(accept);
        component.addExtra(" ");
        component.addExtra(deny);

        return component;
    }

    private boolean isBlockedEitherWay(Connection connection, UUID a, UUID b) throws SQLException {
        return isBlocked(connection, a, b) || isBlocked(connection, b, a);
    }

    private boolean isBlocked(Connection connection, UUID a, UUID b) throws SQLException {
        UUID first = a;
        UUID second = b;
        if(first.toString().compareTo(second.toString()) > 0) {
            first = b;
            second = a;
        }

        try(PreparedStatement ps = connection.prepareStatement(
                "SELECT relation FROM fs_relations WHERE uuid_a = ? AND uuid_b = ? LIMIT 1"
        )) {

            ps.setString(1, first.toString());
            ps.setString(2, second.toString());
            try(ResultSet rs = ps.executeQuery()) {
                if(!rs.next()) {
                    return false;
                }

                return rs.getInt("relation") == Relation.RelationType.BLOCKED.getId();
            }
        }
    }

    public void deny(Player to, UUID fromUuid, String fromName) {
        if(to == null || fromUuid == null) {
            return;
        }

        UUID toUuid = to.getUniqueId();
        SchedulerHelper.async(() -> {
            try(Connection connection = Main.getInstance().getMysql().getConnection()) {
                if(!hasRequest(connection, fromUuid, toUuid)) {
                    SchedulerHelper.sync(() -> to.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.requestNotFound")
                            .replace("%player%", fromName)));
                    return;
                }

                deleteRequest(connection, fromUuid, toUuid);
                SchedulerHelper.sync(() -> to.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.requestDenied")
                        .replace("%player%", fromName)));
            } catch(SQLException e) {
                SchedulerHelper.sync(() -> to.sendMessage("§c§l! §7Database error."));
            }
        });
    }
}

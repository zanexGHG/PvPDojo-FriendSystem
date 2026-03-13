package dev.zanex.friendsystem.commands.impl;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Subcommand;
import dev.zanex.friendsystem.Main;
import dev.zanex.friendsystem.commands.FCommand;
import dev.zanex.friendsystem.utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@CommandAlias("friends|friend|f")
public class FriendsCommand extends FCommand {

    @Default
    public void onDefault(Player player) {
        player.sendMessage(Main.getInstance().getLabelLoader().of("commands.friends.usage"));
    }

    @Subcommand("add")
    @CommandCompletion("@onlineplayers")
    public void onAdd(Player player, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if(target == null || target.getUniqueId() == null) {
            player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.playerNotFound"));

            return;
        }

        Main.getInstance().getFriendService().sendRequest(player, target.getUniqueId(), targetName);
    }

    @Subcommand("accept")
    public void onAccept(Player player, String fromName) {
        OfflinePlayer from = Bukkit.getOfflinePlayer(fromName);
        if(from == null || from.getUniqueId() == null) {
            player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.playerNotFound"));

            return;
        }

        Main.getInstance().getFriendService().accept(player, from.getUniqueId(), fromName);
    }

    @Subcommand("deny")
    public void onDeny(Player player, String fromName) {
        OfflinePlayer from = Bukkit.getOfflinePlayer(fromName);
        if(from == null || from.getUniqueId() == null) {
            player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.playerNotFound"));

            return;
        }

        Main.getInstance().getFriendService().deny(player, from.getUniqueId(), fromName);
    }

    @Subcommand("remove")
    @CommandCompletion("@onlineplayers")
    public void onRemove(Player player, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if(target == null || target.getUniqueId() == null) {
            player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.playerNotFound"));

            return;
        }

        Main.getInstance().getFriendManager().removeRelation(player.getUniqueId(), target.getUniqueId());

        player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.notFriends"));
    }

    @Subcommand("block")
    @CommandCompletion("@onlineplayers")
    public void onBlock(Player player, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if(target == null || target.getUniqueId() == null) {
            player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.playerNotFound"));

            return;
        }

        Main.getInstance().getFriendService().block(player, target.getUniqueId(), targetName);
    }

    @Subcommand("unblock")
    @CommandCompletion("@onlineplayers")
    public void onUnblock(Player player, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if(target == null || target.getUniqueId() == null) {
            player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.playerNotFound"));

            return;
        }

        Main.getInstance().getFriendService().unblock(player, target.getUniqueId(), targetName);
    }

    @Subcommand("list")
    public void onList(Player player) {
        UUID uuid = player.getUniqueId();
        SchedulerHelper.async(() -> {
            List<String> friends = new ArrayList<>();

            try(Connection connection = Main.getInstance().getMysql().getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT uuid_a, uuid_b FROM fs_relations WHERE (uuid_a = ? OR uuid_b = ?) AND relation = 0"
                )) {

                ps.setString(1, uuid.toString());
                ps.setString(2, uuid.toString());

                try(ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        String a = rs.getString("uuid_a");
                        String b = rs.getString("uuid_b");
                        String other = a.equalsIgnoreCase(uuid.toString()) ? b : a;
                        UUID otherUuid = UUID.fromString(other);
                        String name = Bukkit.getOfflinePlayer(otherUuid).getName();

                        friends.add(name == null ? otherUuid.toString() : name);
                    }
                }
            } catch(SQLException e) {
                SchedulerHelper.sync(() -> player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.notFriends")));

                return;
            }

            SchedulerHelper.sync(() -> {
                player.sendMessage(Main.getInstance().getLabelLoader().of("commands.friends.listHeader")
                        .replace("%count%", String.valueOf(friends.size())));

                friends.forEach(name -> {
                    boolean online = Bukkit.getPlayerExact(name) != null;
                    String key = online ? "commands.friends.listEntryOnline" : "commands.friends.listEntryOffline";

                    player.sendMessage(Main.getInstance().getLabelLoader().of(key).replace("%player%", name));
                });
            });
        });
    }
}

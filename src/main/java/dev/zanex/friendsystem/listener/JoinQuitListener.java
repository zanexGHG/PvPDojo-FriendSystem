package dev.zanex.friendsystem.listener;

import dev.zanex.friendsystem.Main;
import dev.zanex.friendsystem.friends.Relation;
import dev.zanex.friendsystem.utils.SchedulerHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JoinQuitListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Main.getInstance().getFriendManager().upsertPlayer(player.getUniqueId());
        Main.getInstance().getFriendService().sendPendingRequests(player);

        loadCache(player.getUniqueId(), relations -> {
            long onlineFriends = relations.stream().filter(relation -> {
                if(!relation.getRelationType().equals(Relation.RelationType.FRIEND)) {
                    return false;
                }

                UUID other = relation.getUuidA().equals(player.getUniqueId()) ? relation.getUuidB() : relation.getUuidA();
                return Main.getInstance().getServer().getPlayer(other) != null;
            }).count();

            player.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.joinMessage")
                    .replace("%online%", String.valueOf(onlineFriends)));

            relations.forEach(relation -> {
                if(!relation.getRelationType().equals(Relation.RelationType.FRIEND)) {
                    return;
                }

                UUID other = relation.getUuidA().equals(player.getUniqueId()) ? relation.getUuidB() : relation.getUuidA();
                Player target = Main.getInstance().getServer().getPlayer(other);
                if(target != null) {
                    target.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.online")
                            .replace("%player%", player.getName()));
                }
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        List<Relation> relations = Main.getInstance().getFriendManager().getPlayerCache().get(player.getUniqueId());
        if(relations == null) {
            return;
        }

        relations.forEach(relation -> {
            if(!relation.getRelationType().equals(Relation.RelationType.FRIEND)) {
                return;
            }

            UUID other = relation.getUuidA().equals(player.getUniqueId()) ? relation.getUuidB() : relation.getUuidA();
            Player target = Main.getInstance().getServer().getPlayer(other);
            if(target != null) {
                target.sendMessage(Main.getInstance().getLabelLoader().of("messages.friend.offline")
                        .replace("%player%", player.getName()));
            }
        });
    }

    private void loadCache(UUID uuid, java.util.function.Consumer<List<Relation>> done) {
        SchedulerHelper.async(() -> {
            List<Relation> relations = new ArrayList<>();

            try(Connection connection = Main.getInstance().getMysql().getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT uuid_a, uuid_b, relation FROM fs_relations WHERE (uuid_a = ? OR uuid_b = ?) AND relation != 1"
                )) {

                ps.setString(1, uuid.toString());
                ps.setString(2, uuid.toString());

                try(ResultSet rs = ps.executeQuery()) {
                    while(rs.next()) {
                        relations.add(new Relation(
                                UUID.fromString(rs.getString("uuid_a")),
                                UUID.fromString(rs.getString("uuid_b")),
                                Relation.ofType(rs.getInt("relation"))
                        ));
                    }
                }
            } catch(SQLException e) {
                SchedulerHelper.sync(() -> done.accept(new ArrayList<>()));

                return;
            }

            Main.getInstance().getFriendManager().getPlayerCache().put(uuid, relations);
            SchedulerHelper.sync(() -> done.accept(relations));
        });
    }
}

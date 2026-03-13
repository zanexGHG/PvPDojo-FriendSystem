package dev.zanex.friendsystem.listener;

import dev.zanex.friendsystem.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class FriendChatListener implements Listener {

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        String prefix = Main.getInstance().getLabelLoader().of("chat.friendPrefix");

        for(Player receiver : event.getRecipients()) {
            if(Main.getInstance().getFriendService().isFriendsCached(receiver.getUniqueId(), sender.getUniqueId())) {
                receiver.sendMessage(prefix + sender.getDisplayName() + "§7: §r" + event.getMessage());
            } else {
                receiver.sendMessage(sender.getDisplayName() + "§7: §r" + event.getMessage());
            }
        }

        event.setCancelled(true);
    }
}

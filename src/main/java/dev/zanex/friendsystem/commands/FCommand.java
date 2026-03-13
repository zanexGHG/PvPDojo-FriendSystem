package dev.zanex.friendsystem.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.RegisteredCommand;
import dev.zanex.friendsystem.Main;
import org.bukkit.command.CommandSender;

public abstract class FCommand extends BaseCommand {

  public FCommand() {
    Main.getInstance().getCommandManager().registerCommand(this);
  }

  @Override
  public boolean canExecute(CommandIssuer issuer, RegisteredCommand<?> command){
    String permission = getPermission();
    if(permission == null || permission.isEmpty() || ((CommandSender) issuer.getIssuer()).hasPermission(permission)) {
        return true;
    } else {
        issuer.sendMessage(Main.getInstance().getLabelLoader().of("messages.noPermission"));
        return false;
    }
  }

  protected String getPermission() {
    Permission perm = this.getClass().getAnnotation(Permission.class);
    return perm != null ? perm.value() : null;
  }
}
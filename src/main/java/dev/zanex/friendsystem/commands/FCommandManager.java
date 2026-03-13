package dev.zanex.friendsystem.commands;

import co.aikar.commands.BukkitCommandCompletionContext;
import co.aikar.commands.BukkitCommandExecutionContext;
import co.aikar.commands.BukkitCommandManager;
import co.aikar.commands.contexts.ContextResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FCommandManager extends BukkitCommandManager {

  public FCommandManager(Plugin plugin) {
    super(plugin);

    registerAsyncCompletion("onlineplayers", context ->
        Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .collect(Collectors.toList())
    );
  }

  public <T> void registerContext(Class<T> clazz,
      ContextResolver<T, BukkitCommandExecutionContext> resolver) {
    getCommandContexts().registerContext(clazz, resolver);
  }

  public void registerAsyncCompletion(String id,
      Function<BukkitCommandCompletionContext, List<String>> supplier) {
    getCommandCompletions().registerAsyncCompletion(id, supplier::apply);
  }
}
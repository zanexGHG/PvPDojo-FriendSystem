package dev.zanex.friendsystem.utils;

import dev.zanex.friendsystem.Main;
import org.bukkit.Bukkit;

public final class SchedulerHelper {
    public static void async(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), runnable);
    }

    public static void sync(Runnable runnable) {
        Bukkit.getScheduler().runTask(Main.getInstance(), runnable);
    }
}

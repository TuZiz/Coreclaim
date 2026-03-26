package com.coreclaim.platform;

import com.coreclaim.CoreClaimPlugin;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class PlatformScheduler {

    private final CoreClaimPlugin plugin;
    private final boolean folia;

    public PlatformScheduler(CoreClaimPlugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    public boolean isFolia() {
        return folia;
    }

    public TaskHandle runRepeating(Runnable runnable, long delayTicks, long periodTicks) {
        if (!folia) {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
            return task::cancel;
        }

        try {
            Object scheduler = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
            Method method = scheduler.getClass().getMethod(
                "runAtFixedRate",
                org.bukkit.plugin.Plugin.class,
                Consumer.class,
                long.class,
                long.class
            );
            Object scheduledTask = method.invoke(
                scheduler,
                plugin,
                (Consumer<Object>) ignored -> runnable.run(),
                delayTicks,
                periodTicks
            );
            return () -> cancelReflectively(scheduledTask);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Folia 调度器适配失败，退回 Bukkit 调度器: " + exception.getMessage());
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
            return task::cancel;
        }
    }

    public void runPlayerTask(Player player, Runnable runnable) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return;
        }

        try {
            Object entityScheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Method method = entityScheduler.getClass().getMethod(
                "run",
                org.bukkit.plugin.Plugin.class,
                Consumer.class,
                Runnable.class
            );
            method.invoke(entityScheduler, plugin, (Consumer<Object>) ignored -> runnable.run(), null);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Folia 玩家调度器适配失败，退回 Bukkit 调度器: " + exception.getMessage());
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    private boolean detectFolia() {
        try {
            plugin.getServer().getClass().getMethod("getGlobalRegionScheduler");
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private void cancelReflectively(Object scheduledTask) {
        if (scheduledTask == null) {
            return;
        }
        try {
            scheduledTask.getClass().getMethod("cancel").invoke(scheduledTask);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @FunctionalInterface
    public interface TaskHandle {
        void cancel();
    }
}

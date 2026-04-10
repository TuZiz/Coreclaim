package com.coreclaim.platform;

import com.coreclaim.CoreClaimPlugin;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class PlatformScheduler {

    private final CoreClaimPlugin plugin;
    private final boolean folia;
    private volatile boolean globalSchedulerAvailable;
    private volatile boolean playerSchedulerAvailable;
    private volatile boolean warnedGlobalFallback;
    private volatile boolean warnedPlayerFallback;

    public PlatformScheduler(CoreClaimPlugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
        this.globalSchedulerAvailable = folia;
        this.playerSchedulerAvailable = folia;
    }

    public boolean isFolia() {
        return folia;
    }

    public TaskHandle runRepeating(Runnable runnable, long delayTicks, long periodTicks) {
        if (!globalSchedulerAvailable) {
            return runBukkitRepeating(runnable, delayTicks, periodTicks);
        }

        try {
            Object scheduler = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
            Object scheduledTask = scheduler.getClass().getMethod(
                "runAtFixedRate",
                org.bukkit.plugin.Plugin.class,
                Consumer.class,
                long.class,
                long.class
            ).invoke(
                scheduler,
                plugin,
                (Consumer<Object>) ignored -> runnable.run(),
                delayTicks,
                periodTicks
            );
            return () -> cancelReflectively(scheduledTask);
        } catch (Throwable exception) {
            disableGlobalScheduler(exception);
            return runBukkitRepeating(runnable, delayTicks, periodTicks);
        }
    }

    public TaskHandle runLater(Runnable runnable, long delayTicks) {
        if (!globalSchedulerAvailable) {
            return runBukkitLater(runnable, delayTicks);
        }

        try {
            Object scheduler = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
            Object scheduledTask = scheduler.getClass().getMethod(
                "runDelayed",
                org.bukkit.plugin.Plugin.class,
                Consumer.class,
                long.class
            ).invoke(
                scheduler,
                plugin,
                (Consumer<Object>) ignored -> runnable.run(),
                delayTicks
            );
            return () -> cancelReflectively(scheduledTask);
        } catch (Throwable exception) {
            disableGlobalScheduler(exception);
            return runBukkitLater(runnable, delayTicks);
        }
    }

    public void runPlayerTask(Player player, Runnable runnable) {
        if (!playerSchedulerAvailable) {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return;
        }

        try {
            Object entityScheduler = player.getClass().getMethod("getScheduler").invoke(player);
            entityScheduler.getClass().getMethod(
                "run",
                org.bukkit.plugin.Plugin.class,
                Consumer.class,
                Runnable.class
            ).invoke(entityScheduler, plugin, (Consumer<Object>) ignored -> runnable.run(), null);
        } catch (Throwable exception) {
            disablePlayerScheduler(exception);
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

    private TaskHandle runBukkitRepeating(Runnable runnable, long delayTicks, long periodTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        return task::cancel;
    }

    private TaskHandle runBukkitLater(Runnable runnable, long delayTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        return task::cancel;
    }

    private void disableGlobalScheduler(Throwable exception) {
        globalSchedulerAvailable = false;
        if (!warnedGlobalFallback) {
            warnedGlobalFallback = true;
            plugin.getLogger().warning("Folia 调度器适配失败，已退回 Bukkit 调度器: " + describeThrowable(exception));
        }
    }

    private void disablePlayerScheduler(Throwable exception) {
        playerSchedulerAvailable = false;
        if (!warnedPlayerFallback) {
            warnedPlayerFallback = true;
            plugin.getLogger().warning("Folia 玩家调度器适配失败，已退回 Bukkit 调度器: " + describeThrowable(exception));
        }
    }

    private String describeThrowable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return cause.getClass().getSimpleName() + ": " + message;
    }

    @FunctionalInterface
    public interface TaskHandle {
        void cancel();
    }
}

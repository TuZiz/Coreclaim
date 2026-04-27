package com.coreclaim.platform;

import com.coreclaim.CoreClaimPlugin;
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
            throw foliaSchedulerFailure("global repeating task", exception);
        }
    }

    public TaskHandle runLater(Runnable runnable, long delayTicks) {
        if (!folia) {
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
            throw foliaSchedulerFailure("global delayed task", exception);
        }
    }

    public void runPlayerTask(Player player, Runnable runnable) {
        if (!folia) {
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
            throw foliaSchedulerFailure("player task", exception);
        }
    }

    public TaskHandle runPlayerLater(Player player, Runnable runnable, long delayTicks) {
        if (!folia) {
            return runBukkitLater(runnable, delayTicks);
        }

        try {
            Object entityScheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Object scheduledTask = entityScheduler.getClass().getMethod(
                "runDelayed",
                org.bukkit.plugin.Plugin.class,
                Consumer.class,
                Runnable.class,
                long.class
            ).invoke(entityScheduler, plugin, (Consumer<Object>) ignored -> runnable.run(), null, delayTicks);
            return () -> cancelReflectively(scheduledTask);
        } catch (Throwable exception) {
            throw foliaSchedulerFailure("player delayed task", exception);
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

    private IllegalStateException foliaSchedulerFailure(String operation, Throwable throwable) {
        return new IllegalStateException("Folia scheduler failed for " + operation + ": " + describeThrowable(throwable), throwable);
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

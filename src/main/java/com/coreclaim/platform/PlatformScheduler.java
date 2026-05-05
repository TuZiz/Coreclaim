package com.coreclaim.platform;

import com.coreclaim.CoreClaimPlugin;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

        long foliaDelayTicks = positiveFoliaTicks(delayTicks);
        long foliaPeriodTicks = positiveFoliaTicks(periodTicks);
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
                foliaDelayTicks,
                foliaPeriodTicks
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

        long foliaDelayTicks = positiveFoliaTicks(delayTicks);
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
                foliaDelayTicks
            );
            return () -> cancelReflectively(scheduledTask);
        } catch (Throwable exception) {
            throw foliaSchedulerFailure("global delayed task", exception);
        }
    }

    public TaskHandle runLocationLater(Location location, Runnable runnable, long delayTicks) {
        if (!folia) {
            return runBukkitLater(runnable, delayTicks);
        }

        Location taskLocation = requireWorldLocation(location);
        long foliaDelayTicks = positiveFoliaTicks(delayTicks);
        try {
            Object scheduler = plugin.getServer().getClass().getMethod("getRegionScheduler").invoke(plugin.getServer());
            Object scheduledTask = scheduler.getClass().getMethod(
                "runDelayed",
                org.bukkit.plugin.Plugin.class,
                Location.class,
                Consumer.class,
                long.class
            ).invoke(
                scheduler,
                plugin,
                taskLocation,
                (Consumer<Object>) ignored -> runnable.run(),
                foliaDelayTicks
            );
            return () -> cancelReflectively(scheduledTask);
        } catch (Throwable exception) {
            throw foliaSchedulerFailure("region delayed task", exception);
        }
    }

    public void runLocationTask(Location location, Runnable runnable) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return;
        }

        Location taskLocation = requireWorldLocation(location);
        try {
            Object scheduler = plugin.getServer().getClass().getMethod("getRegionScheduler").invoke(plugin.getServer());
            scheduler.getClass().getMethod(
                "execute",
                org.bukkit.plugin.Plugin.class,
                Location.class,
                Runnable.class
            ).invoke(scheduler, plugin, taskLocation, runnable);
        } catch (Throwable exception) {
            throw foliaSchedulerFailure("region task", exception);
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

        long foliaDelayTicks = positiveFoliaTicks(delayTicks);
        try {
            Object entityScheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Object scheduledTask = entityScheduler.getClass().getMethod(
                "runDelayed",
                org.bukkit.plugin.Plugin.class,
                Consumer.class,
                Runnable.class,
                long.class
            ).invoke(entityScheduler, plugin, (Consumer<Object>) ignored -> runnable.run(), null, foliaDelayTicks);
            return () -> cancelReflectively(scheduledTask);
        } catch (Throwable exception) {
            throw foliaSchedulerFailure("player delayed task", exception);
        }
    }

    public TaskHandle runPlayerRepeating(Player player, Runnable runnable, long delayTicks, long periodTicks) {
        if (!folia) {
            return runBukkitRepeating(runnable, delayTicks, periodTicks);
        }

        long foliaDelayTicks = positiveFoliaTicks(delayTicks);
        long foliaPeriodTicks = positiveFoliaTicks(periodTicks);
        try {
            Object entityScheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Object scheduledTask = entityScheduler.getClass().getMethod(
                "runAtFixedRate",
                org.bukkit.plugin.Plugin.class,
                Consumer.class,
                Runnable.class,
                long.class,
                long.class
            ).invoke(
                entityScheduler,
                plugin,
                (Consumer<Object>) ignored -> runnable.run(),
                null,
                foliaDelayTicks,
                foliaPeriodTicks
            );
            return () -> cancelReflectively(scheduledTask);
        } catch (Throwable exception) {
            throw foliaSchedulerFailure("player repeating task", exception);
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

    static long positiveFoliaTicks(long ticks) {
        return Math.max(1L, ticks);
    }

    private Location requireWorldLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location must include a loaded world for region scheduling.");
        }
        return location.clone();
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

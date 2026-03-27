package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.item.ClaimCoreFactory;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.platform.PlatformScheduler;
import org.bukkit.entity.Player;

public final class OnlineRewardService {

    private final CoreClaimPlugin plugin;
    private final PlatformScheduler platformScheduler;
    private final ProfileService profileService;
    private final ClaimCoreFactory claimCoreFactory;
    private PlatformScheduler.TaskHandle taskHandle;
    private int saveCounter;

    public OnlineRewardService(
        CoreClaimPlugin plugin,
        PlatformScheduler platformScheduler,
        ProfileService profileService,
        ClaimCoreFactory claimCoreFactory
    ) {
        this.plugin = plugin;
        this.platformScheduler = platformScheduler;
        this.profileService = profileService;
        this.claimCoreFactory = claimCoreFactory;
    }

    public void start() {
        stop();
        this.taskHandle = platformScheduler.runRepeating(this::tickOnlinePlayers, 1200L, 1200L);
    }

    public void stop() {
        if (taskHandle != null) {
            taskHandle.cancel();
            taskHandle = null;
        }
    }

    private void tickOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            platformScheduler.runPlayerTask(player, () -> tickPlayer(player));
        }

        saveCounter++;
        if (saveCounter >= 5) {
            saveCounter = 0;
            profileService.save();
        }
    }

    private void tickPlayer(Player player) {
        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        int before = profile.onlineMinutes();
        profile.addOnlineMinutes(1);

        if (!profile.starterCoreGranted()
            && before < plugin.settings().starterRewardMinutes()
            && profile.onlineMinutes() >= plugin.settings().starterRewardMinutes()) {
            profile.setStarterCoreGranted(true);
            claimCoreFactory.giveStarterCore(player, 1);
            player.sendMessage(
                plugin.message("claim-core-rewarded", "{minutes}", String.valueOf(plugin.settings().starterRewardMinutes()))
            );
            profileService.save();
        }
    }
}


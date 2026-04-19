package com.coreclaim.papi;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.model.Claim;
import com.coreclaim.model.PlayerProfile;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ProfileService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class CoreClaimPlaceholderExpansion extends PlaceholderExpansion {

    private final CoreClaimPlugin plugin;
    private final ProfileService profileService;
    private final ClaimService claimService;

    public CoreClaimPlaceholderExpansion(
        CoreClaimPlugin plugin,
        ProfileService profileService,
        ClaimService claimService
    ) {
        this.plugin = plugin;
        this.profileService = profileService;
        this.claimService = claimService;
    }

    @Override
    public String getIdentifier() {
        return "coreclaim";
    }

    @Override
    public String getAuthor() {
        return "Codex";
    }

    @Override
    public String getVersion() {
        return "1.1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }

        PlayerProfile profile = profileService.getOrCreate(player.getUniqueId(), player.getName());
        Claim claim = claimService.findClaim(player.getLocation()).orElse(null);
        return switch (params.toLowerCase()) {
            case "activity" -> String.valueOf(profile.activityPoints());
            case "online_minutes" -> String.valueOf(profile.onlineMinutes());
            case "claim_count" -> String.valueOf(claimService.countClaims(player.getUniqueId()));
            case "claim_limit" -> String.valueOf(plugin.groups().resolve(player).maxClaims());
            case "starter_core_granted" -> String.valueOf(profile.starterCoreGranted());
            case "next_reward_minutes" -> String.valueOf(Math.max(0, plugin.settings().starterRewardMinutes() - profile.onlineMinutes()));
            case "current_claim_id" -> claim == null ? "" : String.valueOf(claim.id());
            case "current_claim_name" -> claim == null ? "" : claim.name();
            case "current_claim_owner" -> claim == null ? "" : claim.ownerName();
            case "current_claim_radius" -> claim == null ? "" : String.valueOf(claim.displayRadius());
            case "current_claim_trusted_count" -> claim == null ? "0" : String.valueOf(claim.trustedCount());
            default -> null;
        };
    }
}

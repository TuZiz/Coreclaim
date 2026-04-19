package com.coreclaim.service;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.config.ClaimGroup;
import com.coreclaim.economy.EconomyHook;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimSaleListing;
import com.coreclaim.storage.DatabaseManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class ClaimMarketService {

    private final CoreClaimPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final EconomyHook economyHook;

    public ClaimMarketService(
        CoreClaimPlugin plugin,
        DatabaseManager databaseManager,
        ClaimService claimService,
        ProfileService profileService,
        EconomyHook economyHook
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.claimService = claimService;
        this.profileService = profileService;
        this.economyHook = economyHook;
    }

    public List<ClaimSaleListing> listings() {
        List<ClaimSaleListing> listings = databaseManager.query(
            "SELECT claim_id, seller_uuid, seller_name, price, created_at FROM claim_sale_listings ORDER BY created_at DESC",
            statement -> {
            },
            resultSet -> {
                List<ClaimSaleListing> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new ClaimSaleListing(
                        resultSet.getInt("claim_id"),
                        UUID.fromString(resultSet.getString("seller_uuid")),
                        resultSet.getString("seller_name"),
                        resultSet.getDouble("price"),
                        resultSet.getLong("created_at")
                    ));
                }
                return result;
            }
        );
        for (ClaimSaleListing listing : listings) {
            if (marketClaim(listing.claimId()).isEmpty()) {
                claimService.cancelSaleListing(listing.claimId());
            }
        }
        return listings.stream()
            .filter(listing -> marketClaim(listing.claimId()).isPresent())
            .toList();
    }

    public ClaimSaleListing listing(int claimId) {
        return databaseManager.query(
            "SELECT claim_id, seller_uuid, seller_name, price, created_at FROM claim_sale_listings WHERE claim_id = ?",
            statement -> statement.setInt(1, claimId),
            resultSet -> resultSet.next()
                ? new ClaimSaleListing(
                    resultSet.getInt("claim_id"),
                    UUID.fromString(resultSet.getString("seller_uuid")),
                    resultSet.getString("seller_name"),
                    resultSet.getDouble("price"),
                    resultSet.getLong("created_at")
                )
                : null
        );
    }

    public boolean isListed(Claim claim) {
        return claim != null && listing(claim.id()) != null;
    }

    public boolean listClaim(Player seller, Claim claim, double price) {
        if (claim == null) {
            seller.sendMessage(plugin.message("claim-not-found"));
            return false;
        }
        if (databaseManager.isMySql()) {
            claim = claimService.refreshClaimFromDatabase(claim.id()).orElse(null);
            if (claim == null) {
                seller.sendMessage(plugin.message("claim-not-found"));
                return false;
            }
        }
        if (!claim.owner().equals(seller.getUniqueId())) {
            seller.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        if (claim.systemManaged()) {
            seller.sendMessage(chatMessage("system-claim-market-denied", "&c&l! &7系统领地不能上架市场。"));
            return false;
        }
        if (!Double.isFinite(price) || price <= 0D) {
            seller.sendMessage(plugin.message("sale-price-invalid"));
            return false;
        }
        int targetClaimId = claim.id();
        int inserted = databaseManager.update(
            databaseManager.insertIgnoreSql(
                "claim_sale_listings",
                "claim_id, seller_uuid, seller_name, price, created_at",
                "?, ?, ?, ?, ?"
            ),
            statement -> {
                statement.setInt(1, targetClaimId);
                statement.setString(2, seller.getUniqueId().toString());
                statement.setString(3, seller.getName());
                statement.setDouble(4, price);
                statement.setLong(5, Instant.now().getEpochSecond());
            }
        );
        if (inserted <= 0) {
            seller.sendMessage(plugin.message("sale-already-listed", "{name}", claim.name()));
            return false;
        }
        seller.sendMessage(plugin.message(
            "sale-listed",
            "{name}", claim.name(),
            "{price}", ClaimActionService.formatMoney(price)
        ));
        return true;
    }

    public boolean cancelListing(Player seller, Claim claim) {
        if (claim == null) {
            seller.sendMessage(plugin.message("claim-not-found"));
            return false;
        }
        if (databaseManager.isMySql()) {
            claim = claimService.refreshClaimFromDatabase(claim.id()).orElse(null);
            if (claim == null) {
                seller.sendMessage(plugin.message("claim-not-found"));
                return false;
            }
        }
        if (!claim.owner().equals(seller.getUniqueId()) && !seller.hasPermission("coreclaim.admin")) {
            seller.sendMessage(plugin.message("trust-no-permission"));
            return false;
        }
        if (claim.systemManaged() && !seller.hasPermission("coreclaim.admin")) {
            seller.sendMessage(chatMessage("system-claim-market-denied", "&c&l! &7系统领地不能由普通玩家管理市场状态。"));
            return false;
        }
        int targetClaimId = claim.id();
        int deleted = databaseManager.update(
            "DELETE FROM claim_sale_listings WHERE claim_id = ?",
            statement -> statement.setInt(1, targetClaimId)
        );
        if (deleted <= 0) {
            seller.sendMessage(plugin.message("sale-not-listed", "{name}", claim.name()));
            return false;
        }
        seller.sendMessage(plugin.message("sale-cancelled", "{name}", claim.name()));
        return true;
    }

    public boolean purchase(Player buyer, int claimId) {
        ClaimSaleListing listing = listing(claimId);
        if (listing == null) {
            buyer.sendMessage(plugin.message("sale-listing-missing"));
            return false;
        }
        Claim claim = purchaseClaim(claimId);
        if (claim == null) {
            claimService.cancelSaleListing(claimId);
            buyer.sendMessage(plugin.message("claim-not-found"));
            return false;
        }
        if (!claim.owner().equals(listing.sellerId())) {
            claimService.cancelSaleListing(claimId);
            buyer.sendMessage(plugin.message("sale-listing-missing"));
            return false;
        }
        if (claim.systemManaged()) {
            claimService.cancelSaleListing(claimId);
            buyer.sendMessage(chatMessage("system-claim-market-denied", "&c&l! &7系统领地不能通过市场交易。"));
            return false;
        }
        if (buyer.getUniqueId().equals(listing.sellerId())) {
            buyer.sendMessage(plugin.message("sale-own-claim"));
            return false;
        }
        if (!hasClaimSlot(buyer)) {
            buyer.sendMessage(plugin.message("sale-buyer-no-slot"));
            return false;
        }
        if (!economyHook.available()) {
            buyer.sendMessage(plugin.message("economy-missing"));
            return false;
        }
        if (!economyHook.has(buyer, listing.price())) {
            buyer.sendMessage(plugin.message("economy-not-enough", "{cost}", ClaimActionService.formatMoney(listing.price())));
            return false;
        }

        int reserved = databaseManager.update(
            "DELETE FROM claim_sale_listings WHERE claim_id = ? AND seller_uuid = ?",
            statement -> {
                statement.setInt(1, listing.claimId());
                statement.setString(2, listing.sellerId().toString());
            }
        );
        if (reserved <= 0) {
            buyer.sendMessage(plugin.message("sale-listing-missing"));
            return false;
        }

        if (!economyHook.withdraw(buyer, listing.price())) {
            restoreListing(listing, "withdraw failed");
            buyer.sendMessage(plugin.message("economy-missing"));
            return false;
        }

        boolean transferred = claimService.transferClaim(claim, buyer.getUniqueId(), buyer.getName());
        if (!transferred) {
            if (!economyHook.deposit(buyer, listing.price())) {
                logCompensationFailure("refund buyer after transfer failure", listing, buyer);
            }
            restoreListing(listing, "transfer failed");
            buyer.sendMessage(plugin.message("sale-purchase-failed"));
            return false;
        }

        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerId());
        if (!economyHook.deposit(seller, listing.price())) {
            boolean refundedBuyer = economyHook.deposit(buyer, listing.price());
            boolean revertedClaim = claimService.transferClaim(claim, listing.sellerId(), listing.sellerName());
            restoreListing(listing, "seller deposit failed");
            if (!refundedBuyer || !revertedClaim) {
                plugin.getLogger().severe(
                    "Failed to fully compensate claim sale deposit failure. claimId=" + listing.claimId()
                        + ", buyer=" + buyer.getName()
                        + ", seller=" + listing.sellerName()
                        + ", price=" + listing.price()
                        + ", refundedBuyer=" + refundedBuyer
                        + ", revertedClaim=" + revertedClaim
                );
            }
            buyer.sendMessage(plugin.message("sale-purchase-failed"));
            return false;
        }

        buyer.sendMessage(plugin.message(
            "sale-purchase-success",
            "{name}", claim.name(),
            "{seller}", listing.sellerName(),
            "{price}", ClaimActionService.formatMoney(listing.price())
        ));
        Player onlineSeller = Bukkit.getPlayer(listing.sellerId());
        if (onlineSeller != null) {
            String sellerMessage = plugin.message(
                "sale-seller-paid",
                "{name}", claim.name(),
                "{buyer}", buyer.getName(),
                "{price}", ClaimActionService.formatMoney(listing.price())
            );
            plugin.platformScheduler().runPlayerTask(onlineSeller, () -> onlineSeller.sendMessage(sellerMessage));
        }
        return true;
    }

    private boolean hasClaimSlot(Player buyer) {
        ClaimGroup group = plugin.groups().resolve(buyer);
        int maxClaims = group.maxClaims();
        return claimService.countClaims(buyer.getUniqueId()) < maxClaims;
    }

    private java.util.Optional<Claim> marketClaim(int claimId) {
        return databaseManager.isMySql()
            ? claimService.refreshClaimFromDatabase(claimId)
            : claimService.findClaimByIdOrLoad(claimId);
    }

    private Claim purchaseClaim(int claimId) {
        return (databaseManager.isMySql()
            ? claimService.refreshClaimFromDatabase(claimId)
            : claimService.findClaimByIdOrLoad(claimId)
        ).orElse(null);
    }

    private void restoreListing(ClaimSaleListing listing, String reason) {
        int restored = databaseManager.update(
            databaseManager.insertIgnoreSql(
                "claim_sale_listings",
                "claim_id, seller_uuid, seller_name, price, created_at",
                "?, ?, ?, ?, ?"
            ),
            statement -> {
                statement.setInt(1, listing.claimId());
                statement.setString(2, listing.sellerId().toString());
                statement.setString(3, listing.sellerName());
                statement.setDouble(4, listing.price());
                statement.setLong(5, listing.createdAt());
            }
        );
        if (restored <= 0) {
            plugin.getLogger().warning(
                "Could not restore sale listing after " + reason + ". claimId=" + listing.claimId()
                    + ", seller=" + listing.sellerName()
                    + ", price=" + listing.price()
            );
        }
    }

    private void logCompensationFailure(String action, ClaimSaleListing listing, Player buyer) {
        plugin.getLogger().severe(
            "Failed to " + action + " for claim sale. claimId=" + listing.claimId()
                + ", buyer=" + buyer.getName()
                + ", seller=" + listing.sellerName()
                + ", price=" + listing.price()
        );
    }

    private String chatMessage(String path, String fallback, String... replacements) {
        String prefix = plugin.messagesConfig().getString("prefix", "&8[&6Claim&8] &f");
        String body = plugin.messagesConfig().contains(path) ? plugin.messagesConfig().getString(path, fallback) : fallback;
        String message = plugin.color(prefix + body);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }
}

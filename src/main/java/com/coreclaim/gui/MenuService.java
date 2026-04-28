package com.coreclaim.gui;

import com.coreclaim.CoreClaimPlugin;
import com.coreclaim.gui.controller.ClaimExpansionMenuController;
import com.coreclaim.gui.controller.ClaimListMenuController;
import com.coreclaim.gui.controller.ClaimManageMenuController;
import com.coreclaim.gui.controller.ClaimViewMenuController;
import com.coreclaim.gui.controller.CoreMenuController;
import com.coreclaim.gui.controller.PermissionMenuController;
import com.coreclaim.gui.controller.SelectionCreateMenuController;
import com.coreclaim.gui.controller.TrustMenuController;
import com.coreclaim.gui.holder.BaseHolder;
import com.coreclaim.gui.holder.ClaimExpandAmountHolder;
import com.coreclaim.gui.holder.ClaimExpandConfirmHolder;
import com.coreclaim.gui.holder.ClaimListHolder;
import com.coreclaim.gui.holder.ClaimManageHolder;
import com.coreclaim.gui.holder.ClaimPermissionHolder;
import com.coreclaim.gui.holder.ClaimViewHolder;
import com.coreclaim.gui.holder.CoreMenuHolder;
import com.coreclaim.gui.holder.SelectionCreateHolder;
import com.coreclaim.gui.holder.TrustMenuHolder;
import com.coreclaim.gui.holder.TrustOnlineAddHolder;
import com.coreclaim.gui.support.MenuConfigAccessor;
import com.coreclaim.gui.support.MenuItemFactory;
import com.coreclaim.gui.support.MenuTextFormatter;
import com.coreclaim.model.Claim;
import com.coreclaim.model.ClaimFlag;
import com.coreclaim.model.ClaimFlagState;
import com.coreclaim.model.ClaimDirection;
import com.coreclaim.service.ClaimActionService;
import com.coreclaim.service.ClaimInputService;
import com.coreclaim.service.ClaimSelectionService;
import com.coreclaim.service.ClaimService;
import com.coreclaim.service.ClaimService.ClaimListEntry;
import com.coreclaim.service.ClaimService.ClaimListRelation;
import com.coreclaim.service.ProfileService;
import com.coreclaim.service.RemovalConfirmationService;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class MenuService {

    private final CoreClaimPlugin plugin;
    private final ClaimService claimService;
    private final ProfileService profileService;
    private final ClaimActionService claimActionService;
    private final RemovalConfirmationService removalConfirmationService;
    private final ClaimInputService claimInputService;
    private final ClaimSelectionService claimSelectionService;
    private final MenuConfigAccessor configAccessor;
    private final MenuTextFormatter textFormatter;
    private final MenuItemFactory itemFactory;
    private final ClaimExpansionMenuSupport expansionSupport;
    private final ClaimListMenuController claimListMenuController;
    private final ClaimManageMenuController claimManageMenuController;
    private final ClaimExpansionMenuController claimExpansionMenuController;
    private final CoreMenuController coreMenuController;
    private final ClaimViewMenuController claimViewMenuController;
    private final TrustMenuController trustMenuController;
    private final PermissionMenuController permissionMenuController;
    private final SelectionCreateMenuController selectionCreateMenuController;

    public MenuService(
        CoreClaimPlugin plugin,
        ClaimService claimService,
        ProfileService profileService,
        ClaimActionService claimActionService,
        RemovalConfirmationService removalConfirmationService,
        ClaimInputService claimInputService,
        ClaimSelectionService claimSelectionService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.profileService = profileService;
        this.claimActionService = claimActionService;
        this.removalConfirmationService = removalConfirmationService;
        this.claimInputService = claimInputService;
        this.claimSelectionService = claimSelectionService;
        this.configAccessor = new MenuConfigAccessor(plugin);
        this.textFormatter = new MenuTextFormatter(plugin);
        this.itemFactory = new MenuItemFactory(plugin, configAccessor, textFormatter);
        this.expansionSupport = new ClaimExpansionMenuSupport(plugin, claimActionService);
        this.claimListMenuController = new ClaimListMenuController(this);
        this.claimManageMenuController = new ClaimManageMenuController(this);
        this.claimExpansionMenuController = new ClaimExpansionMenuController(this);
        this.coreMenuController = new CoreMenuController(this);
        this.claimViewMenuController = new ClaimViewMenuController(this);
        this.trustMenuController = new TrustMenuController(this);
        this.permissionMenuController = new PermissionMenuController(this);
        this.selectionCreateMenuController = new SelectionCreateMenuController(this);
    }

    public void openMainMenu(Player player) {
        Claim currentClaim = claimActionService.findOwnedClaim(player);
        if (currentClaim != null) {
            openCoreMenu(player, currentClaim);
            return;
        }
        openClaimListMenu(player, 0);
    }

    public void openClaimListMenu(Player player, int page) {
        claimListMenuController.open(player, page);
    }

    public void openClaimManageMenu(Player player, Claim claim) {
        claimManageMenuController.open(player, claim);
    }

    public void openClaimExpandAmountMenu(Player player, Claim claim, ClaimDirection direction, int amount) {
        claimExpansionMenuController.openAmount(player, claim, direction, amount);
    }

    public void openClaimExpandConfirmMenu(Player player, Claim claim, ClaimDirection direction, int amount) {
        claimExpansionMenuController.openConfirm(player, claim, direction, amount);
    }

    public void openCoreMenu(Player player, Claim claim) {
        coreMenuController.open(player, claim);
    }

    public void openClaimViewMenu(Player player, int claimId, int page) {
        claimViewMenuController.open(player, claimId, page);
    }

    public void openTrustMenu(Player player, Claim claim, int page) {
        trustMenuController.openTrust(player, claim, page);
    }

    public void openTrustOnlineAddMenu(Player player, Claim claim, int page, int returnPage) {
        trustMenuController.openOnlineAdd(player, claim, page, returnPage);
    }

    public void openClaimPermissionsMenu(Player player, Claim claim) {
        permissionMenuController.open(player, claim);
    }

    public void openSelectionCreateMenu(Player player, String claimName, ClaimSelectionService.SelectionPreview preview) {
        selectionCreateMenuController.open(player, claimName, preview);
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof BaseHolder holder)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        if (holder instanceof ClaimListHolder claimListHolder) {
            handleClaimListMenu(player, claimListHolder, slot, event.isRightClick());
        } else if (holder instanceof ClaimManageHolder claimManageHolder) {
            handleClaimManageMenu(player, claimManageHolder, slot);
        } else if (holder instanceof ClaimExpandAmountHolder claimExpandAmountHolder) {
            handleClaimExpandAmountMenu(player, claimExpandAmountHolder, slot);
        } else if (holder instanceof ClaimExpandConfirmHolder claimExpandConfirmHolder) {
            handleClaimExpandConfirmMenu(player, claimExpandConfirmHolder, slot);
        } else if (holder instanceof CoreMenuHolder coreMenuHolder) {
            handleCoreMenu(player, coreMenuHolder, slot, event.isRightClick());
        } else if (holder instanceof ClaimViewHolder claimViewHolder) {
            handleClaimViewMenu(player, claimViewHolder, slot);
        } else if (holder instanceof TrustMenuHolder trustMenuHolder) {
            handleTrustMenu(player, trustMenuHolder, slot);
        } else if (holder instanceof TrustOnlineAddHolder trustOnlineAddHolder) {
            handleTrustOnlineAddMenu(player, trustOnlineAddHolder, slot);
        } else if (holder instanceof ClaimPermissionHolder permissionHolder) {
            handlePermissionMenu(player, permissionHolder, slot, event.isRightClick());
        } else if (holder instanceof SelectionCreateHolder selectionCreateHolder) {
            handleSelectionCreateMenu(player, selectionCreateHolder, slot);
        }
    }

    private void handleClaimListMenu(Player player, ClaimListHolder holder, int slot, boolean rightClick) {
        claimListMenuController.handle(player, holder, slot, rightClick);
    }

    private void handleClaimManageMenu(Player player, ClaimManageHolder holder, int slot) {
        claimManageMenuController.handle(player, holder, slot);
    }

    private void handleClaimExpandAmountMenu(Player player, ClaimExpandAmountHolder holder, int slot) {
        claimExpansionMenuController.handleAmount(player, holder, slot);
    }

    private void handleClaimExpandConfirmMenu(Player player, ClaimExpandConfirmHolder holder, int slot) {
        claimExpansionMenuController.handleConfirm(player, holder, slot);
    }

    private void handleCoreMenu(Player player, CoreMenuHolder holder, int slot, boolean rightClick) {
        coreMenuController.handle(player, holder, slot, rightClick);
    }

    private void handleClaimViewMenu(Player player, ClaimViewHolder holder, int slot) {
        claimViewMenuController.handle(player, holder, slot);
    }

    private void handleTrustMenu(Player player, TrustMenuHolder holder, int slot) {
        trustMenuController.handleTrust(player, holder, slot);
    }

    private void handleTrustOnlineAddMenu(Player player, TrustOnlineAddHolder holder, int slot) {
        trustMenuController.handleOnlineAdd(player, holder, slot);
    }

    private void handlePermissionMenu(Player player, ClaimPermissionHolder holder, int slot, boolean rightClick) {
        permissionMenuController.handle(player, holder, slot, rightClick);
    }

    private void handleSelectionCreateMenu(Player player, SelectionCreateHolder holder, int slot) {
        selectionCreateMenuController.handle(player, holder, slot);
    }

    public CoreClaimPlugin plugin() {
        return plugin;
    }

    public ClaimService claimService() {
        return claimService;
    }

    public ProfileService profileService() {
        return profileService;
    }

    public ClaimActionService claimActionService() {
        return claimActionService;
    }

    public ClaimInputService claimInputService() {
        return claimInputService;
    }

    public RemovalConfirmationService removalConfirmationService() {
        return removalConfirmationService;
    }

    public ClaimSelectionService claimSelectionService() {
        return claimSelectionService;
    }

    public ClaimExpansionMenuSupport expansionSupport() {
        return expansionSupport;
    }

    public void fill(Inventory inventory, String menuKey, String itemKey) {
        ItemStack filler = configuredItem(menuKey, itemKey);
        List<Integer> fillerSlots = slots(menuKey, itemKey);
        if (fillerSlots.isEmpty()) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler.clone());
            }
            return;
        }
        for (int slot : fillerSlots) {
            inventory.setItem(slot, filler.clone());
        }
    }

    public FileConfiguration menu(String menuKey) {
        return configAccessor.menu(menuKey);
    }

    public boolean hasItem(String menuKey, String itemKey) {
        return configAccessor.hasItem(menuKey, itemKey);
    }

    public int menuSize(String menuKey) {
        return configAccessor.menuSize(menuKey);
    }

    public String menuTitle(String menuKey, String... replacements) {
        return textFormatter.menuTitle(menu(menuKey).getString("title", menuKey), replacements);
    }

    public int slot(String menuKey, String itemKey) {
        return configAccessor.slot(menuKey, itemKey, textFormatter::padLayout);
    }

    public List<Integer> slots(String menuKey, String itemKey) {
        return configAccessor.slots(menuKey, itemKey, textFormatter::padLayout);
    }

    public ItemStack configuredItem(String menuKey, String itemKey, String... replacements) {
        return itemFactory.configuredItem(menuKey, itemKey, replacements);
    }

    public ItemStack playerHead(String menuKey, String itemKey, UUID playerId, String... replacements) {
        return itemFactory.playerHead(menuKey, itemKey, playerId, replacements);
    }

    public void playConfiguredSound(Player player, String menuKey, String itemKey) {
        itemFactory.playConfiguredSound(player, menuKey, itemKey);
    }

    public String displayNotifyPreview(String raw, Claim claim, String fallback) {
        return textFormatter.displayNotifyPreview(raw, claim, fallback);
    }

    public String notifyStateText(String raw) {
        return textFormatter.notifyStateText(raw);
    }

    public ClaimListEntry resolveVisibleListEntry(Player player, int claimId) {
        Claim claim = claimService.findClaimByIdFresh(claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return null;
        }
        ClaimListEntry entry = claimService.visibleClaimEntryFresh(player.getUniqueId(), claimId).orElse(null);
        if (entry == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("trust-no-permission"));
            return null;
        }
        return entry;
    }

    public Claim resolveTrustedViewClaim(Player player, int claimId) {
        Claim claim = claimService.findClaimByIdFresh(claimId).orElse(null);
        if (claim == null) {
            player.closeInventory();
            player.sendMessage(plugin.message("claim-not-found"));
            return null;
        }
        if (claim.owner().equals(player.getUniqueId())) {
            openCoreMenu(player, claim);
            return null;
        }
        if (!claimService.countsTowardQuota(claim) || claim.isDenied(player.getUniqueId()) || !claim.isTrusted(player.getUniqueId())) {
            player.closeInventory();
            player.sendMessage(plugin.message("trust-no-permission"));
            return null;
        }
        return claim;
    }

    public void sendClaimViewDetails(Player player, Claim claim) {
        player.sendMessage(plugin.color("&#F59E0B[Claim] &#F8FAFC\u9886\u5730\u540d\u79f0: &#FFD166" + claim.name()));
        player.sendMessage(plugin.color("&#F59E0B[Claim] &#F8FAFC\u9886\u5730\u4e3b\u4eba: &#4CC9F0" + claim.ownerName()));
        player.sendMessage(plugin.color("&#F59E0B[Claim] &#F8FAFC\u6240\u5c5e\u533a\u670d: &#FFD166" + claimService.displayServerId(claim)));
        player.sendMessage(plugin.color("&#F59E0B[Claim] &#F8FAFC\u6240\u5728\u4e16\u754c: &#FFD166" + claim.world()));
        player.sendMessage(plugin.color("&#F59E0B[Claim] &#F8FAFC\u6838\u5fc3\u5750\u6807: &#F8FAFC" + claim.centerX() + ", " + claim.centerY() + ", " + claim.centerZ()));
        player.sendMessage(plugin.color("&#F59E0B[Claim] &#F8FAFC\u9886\u5730\u5927\u5c0f: &#FFD166" + claim.width() + "x" + claim.depth() + " &#CBD5E1(\u9762\u79ef " + claim.area() + ")"));
        player.sendMessage(plugin.color("&#F59E0B[Claim] &#F8FAFC\u4f20\u9001\u70b9: " + (claim.hasTeleportPoint() ? "&#55FFAA\u5df2\u8bbe\u7f6e" : "&#FFD166\u672a\u8bbe\u7f6e\uff0c\u9ed8\u8ba4\u56de\u6838\u5fc3")));
        player.sendMessage(plugin.color("&#F59E0B[Claim] &#F8FAFC\u6210\u5458\u6570\u91cf: &#FFD166" + claim.trustedCount()));
    }

    public String relationText(ClaimListRelation relation) {
        return textFormatter.relationText(relation);
    }

    public String leftClickActionText(ClaimListRelation relation) {
        return textFormatter.leftClickActionText(relation);
    }

    public String stripMessagePrefix(String message) {
        return textFormatter.stripMessagePrefix(message);
    }

    public String stateText(boolean enabled) {
        return textFormatter.stateText(enabled);
    }

    public String defaultStateText(boolean enabled) {
        return textFormatter.defaultStateText(enabled);
    }

    public String flagStateText(ClaimFlagState state) {
        return textFormatter.flagStateText(state);
    }

    public String flagStateText(Claim claim, ClaimFlag flag, ClaimFlagState state) {
        if (flag == ClaimFlag.TIME_CYCLE) {
            return flagStateText(flag, state);
        }
        if (state == ClaimFlagState.UNSET) {
            ClaimFlagState defaultState = plugin.settings().claimFlagDefault(flag, claim.systemManaged());
            if (defaultState != ClaimFlagState.UNSET) {
                return flagStateText(flag, defaultState);
            }
            return defaultStateText(claim.permission(flag.fallbackPermission()));
        }
        return flagStateText(state);
    }

    public String flagStateText(ClaimFlag flag, ClaimFlagState state) {
        if (flag != ClaimFlag.TIME_CYCLE) {
            return flagStateText(state);
        }
        return switch (state) {
            case ALLOW -> "&#FFD166\u767d\u5929";
            case DENY -> "&#60A5FA\u591c\u665a";
            case UNSET -> "&#CBD5E1\u8ddf\u968f\u4e16\u754c\u65f6\u95f4";
        };
    }

    public int countCustomFlags(Claim claim) {
        int count = 0;
        for (ClaimFlag flag : ClaimFlag.values()) {
            if (claim.flagState(flag) != ClaimFlagState.UNSET) {
                count++;
            }
        }
        return count;
    }

    public String flagItemKey(ClaimFlag flag) {
        return textFormatter.flagItemKey(flag);
    }

    public String playerName(UUID playerId) {
        return textFormatter.playerName(playerId);
    }

    private String padLayout(String line) {
        return textFormatter.padLayout(line);
    }

    private String apply(String text, String... replacements) {
        return textFormatter.apply(text, replacements);
    }

    private ItemStack item(Material material, String name, String... lore) {
        return itemFactory.item(material, name, lore);
    }

}

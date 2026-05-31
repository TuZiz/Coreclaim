package com.coreclaim.protection.listener;

import static com.coreclaim.protection.listener.BlockProtectionListener.PreCancelledInteractionResolution.ALLOW_AXE_STRIPPING;
import static com.coreclaim.protection.listener.BlockProtectionListener.PreCancelledInteractionResolution.ALLOW_COMPOSTER;
import static com.coreclaim.protection.listener.BlockProtectionListener.PreCancelledInteractionResolution.ALLOW_BLOCK_DRIVEN_TOOL_CHANGE;
import static com.coreclaim.protection.listener.BlockProtectionListener.PreCancelledInteractionResolution.ALLOW_CAKE_CONSUMPTION;
import static com.coreclaim.protection.listener.BlockProtectionListener.PreCancelledInteractionResolution.DENY;
import static com.coreclaim.protection.listener.BlockProtectionListener.PreCancelledInteractionResolution.IGNORE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BlockProtectionListenerTest {

    @Test
    void preCancelledCakeAllowsPlayersWithInteractPermission() {
        assertEquals(
            ALLOW_CAKE_CONSUMPTION,
            BlockProtectionListener.resolvePreCancelledInteraction(true, true, false, false, true, false)
        );
    }

    @Test
    void preCancelledCakeDeniesPlayersWithoutInteractPermission() {
        assertEquals(
            DENY,
            BlockProtectionListener.resolvePreCancelledInteraction(true, true, false, false, false, true)
        );
    }

    @Test
    void preCancelledCakeAllowsBypassPlayers() {
        assertEquals(
            ALLOW_CAKE_CONSUMPTION,
            BlockProtectionListener.resolvePreCancelledInteraction(true, true, false, true, false, false)
        );
    }

    @Test
    void preCancelledComposterAllowsPlayersWithInteractPermission() {
        assertEquals(
            ALLOW_COMPOSTER,
            BlockProtectionListener.resolvePreCancelledInteraction(true, false, true, false, false, false, true, false, false)
        );
    }

    @Test
    void preCancelledComposterDeniesPlayersWithoutInteractPermission() {
        assertEquals(
            DENY,
            BlockProtectionListener.resolvePreCancelledInteraction(true, false, true, false, false, false, false, false, false)
        );
    }

    @Test
    void preCancelledAxeStrippingAllowsPlayersWhoCanAccessClaim() {
        assertEquals(
            ALLOW_AXE_STRIPPING,
            BlockProtectionListener.resolvePreCancelledInteraction(true, false, true, false, false, true)
        );
    }

    @Test
    void preCancelledAxeStrippingDeniesPlayersWhoCannotAccessClaim() {
        assertEquals(
            DENY,
            BlockProtectionListener.resolvePreCancelledInteraction(true, false, true, false, false, false)
        );
    }

    @Test
    void preCancelledUnknownInteractionsStayUntouched() {
        assertEquals(
            IGNORE,
            BlockProtectionListener.resolvePreCancelledInteraction(true, false, false, false, false, false)
        );
    }

    @Test
    void preCancelledBlockDrivenToolChangesAllowPlayersWithToolPermission() {
        assertEquals(
            ALLOW_BLOCK_DRIVEN_TOOL_CHANGE,
            BlockProtectionListener.resolvePreCancelledInteraction(true, false, false, false, true, false, false, true, false)
        );
    }

    @Test
    void preCancelledBlockDrivenToolChangesDenyPlayersWithoutToolPermission() {
        assertEquals(
            DENY,
            BlockProtectionListener.resolvePreCancelledInteraction(true, false, false, false, true, false, true, false, true)
        );
    }

    @Test
    void preCancelledBlockDrivenToolChangesAllowBypassPlayers() {
        assertEquals(
            ALLOW_BLOCK_DRIVEN_TOOL_CHANGE,
            BlockProtectionListener.resolvePreCancelledInteraction(true, false, false, false, true, true, false, false, false)
        );
    }

    @Test
    void blockDrivenToolChangesUseCompatApplierInAllAllowedBranches() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/coreclaim/protection/listener/BlockProtectionListener.java"));

        assertFalse(source.contains("allowVanillaBlockDrivenToolChange"));
        assertTrue(occurrences(source, "ProtectionInteractionCompat.applyBlockDrivenToolChange(event)") >= 3);
        assertTrue(source.contains("ProtectionInteractionCompat.applyComposterInteraction(event)"));
        assertTrue(source.contains("\"tool-change-block-use-allow\", \"permission=\" + toolChangePermission + \" applied=\" + applied"));
        assertTrue(source.contains("\"bypass-block-tool\", \"applied=\" + applied"));
        assertTrue(source.contains("\"pre-cancel-block-tool-allow\", \"tool=\" + formatDecision(toolChangeDecision) + \" applied=\" + applied"));
        assertTrue(source.contains("\"composter-allow\", \"interact=\" + formatDecision(interactDecision) + \" applied=\" + applied"));
        assertTrue(source.contains("\"pre-cancel-composter-allow\", \"interact=\" + formatDecision(interactDecision) + \" applied=\" + applied"));
        assertTrue(source.contains("\"bypass-composter\", \"applied=\" + applied"));
    }

    @Test
    void tntIgnitionUsesExplosionPermissionBeforeGenericInteractAllowList() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/coreclaim/protection/listener/BlockProtectionListener.java"));

        int tntBranch = source.indexOf("boolean tntIgnition = support.isTntIgnition(clickedType, event.getItem());");
        int allowListed = source.indexOf("boolean allowListed = support.plugin().settings().isAllowedInteract(clickedType)");
        assertTrue(tntBranch > 0);
        assertTrue(allowListed > tntBranch);
        assertTrue(source.contains("debugInteract(event, claim, \"tnt-ignite-allow\", \"permission=\" + requiredPermission);"));
        assertTrue(source.contains("debugInteract(event, claim, \"tnt-ignite-deny\", \"permission=\" + requiredPermission);"));
        assertTrue(source.contains("support.explosionAuthorizationService().authorize(event.getClickedBlock().getLocation());"));
    }

    @Test
    void rightClickDenySetsBothPlayerInteractResults() {
        PlayerInteractEvent event = new PlayerInteractEvent(
            null,
            Action.RIGHT_CLICK_BLOCK,
            new ItemStack(Material.GLOWSTONE),
            block(Material.RESPAWN_ANCHOR),
            BlockFace.UP,
            EquipmentSlot.HAND
        );

        BlockProtectionListener.denyRightClickInteraction(event);

        assertTrue(event.isCancelled());
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        assertEquals(Event.Result.DENY, event.useItemInHand());
    }

    @Test
    void denyBranchesUseHardPlayerInteractDeny() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/coreclaim/protection/listener/BlockProtectionListener.java"));

        assertTrue(occurrences(source, "denyRightClickInteraction(event);") >= 7);
    }

    @Test
    void iceBreakWaterRestoreOnlyTriggersForNormalIceWithoutSilkTouchInSurvival() {
        assertTrue(BlockProtectionListener.isWaterRestoringIce(Material.ICE, GameMode.SURVIVAL, false));
        assertTrue(BlockProtectionListener.isWaterRestoringIce(Material.FROSTED_ICE, GameMode.SURVIVAL, false));
        assertFalse(BlockProtectionListener.isWaterRestoringIce(Material.ICE, GameMode.SURVIVAL, true));
        assertFalse(BlockProtectionListener.isWaterRestoringIce(Material.PACKED_ICE, GameMode.SURVIVAL, false));
        assertFalse(BlockProtectionListener.isWaterRestoringIce(Material.ICE, GameMode.CREATIVE, false));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static Block block(Material material) {
        return (Block)Proxy.newProxyInstance(
            Block.class.getClassLoader(),
            new Class<?>[] {Block.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getType" -> material;
                case "toString" -> "Block:" + material;
                case "hashCode" -> material.hashCode();
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte)0;
        }
        if (returnType == short.class) {
            return (short)0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }
}

package com.coreclaim.protection.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.data.type.Cake;
import org.junit.jupiter.api.Test;

class ProtectionInteractionCompatTest {

    @Test
    void cakeConsumptionIncrementsBitesAndFoodInSurvival() {
        CakeAccess access = new CakeAccess(GameMode.SURVIVAL, 10, 1.0F, 0);

        assertTrue(ProtectionInteractionCompat.applyCakeConsumption(access));

        assertEquals(1, access.bites);
        assertEquals(12, access.foodLevel);
        assertEquals(1.4F, access.saturation);
        assertTrue(access.updatedCake);
        assertTrue(access.playedSound);
        assertTrue(access.finishedApplied);
    }

    @Test
    void cakeConsumptionStillAppliesWhenSurvivalFoodIsFull() {
        CakeAccess access = new CakeAccess(GameMode.SURVIVAL, 20, 3.0F, 0);

        assertTrue(ProtectionInteractionCompat.applyCakeConsumption(access));

        assertEquals(1, access.bites);
        assertEquals(20, access.foodLevel);
        assertEquals(3.0F, access.saturation);
        assertTrue(access.updatedCake);
        assertTrue(access.finishedApplied);
    }

    @Test
    void cakeConsumptionAppliesInCreativeWithoutFoodChange() {
        CakeAccess access = new CakeAccess(GameMode.CREATIVE, 20, 5.0F, 0);

        assertTrue(ProtectionInteractionCompat.applyCakeConsumption(access));

        assertEquals(1, access.bites);
        assertEquals(20, access.foodLevel);
        assertEquals(5.0F, access.saturation);
        assertTrue(access.updatedCake);
        assertTrue(access.finishedApplied);
    }

    @Test
    void cakeConsumptionDoesNotApplyInSpectator() {
        CakeAccess access = new CakeAccess(GameMode.SPECTATOR, 20, 5.0F, 0);

        assertFalse(ProtectionInteractionCompat.applyCakeConsumption(access));

        assertEquals(0, access.bites);
        assertTrue(access.allowedVanilla);
        assertFalse(access.updatedCake);
        assertFalse(access.finishedApplied);
    }

    @Test
    void cakeConsumptionRemovesCakeOnLastBite() {
        CakeAccess access = new CakeAccess(GameMode.SURVIVAL, 18, 2.0F, 6);

        assertTrue(ProtectionInteractionCompat.applyCakeConsumption(access));

        assertTrue(access.removedCake);
        assertFalse(access.updatedCake);
        assertTrue(access.finishedApplied);
    }

    private static final class CakeAccess implements ProtectionInteractionCompat.CakeConsumptionAccess {
        private final GameMode gameMode;
        private int foodLevel;
        private float saturation;
        private int bites;
        private boolean updatedCake;
        private boolean removedCake;
        private boolean playedSound;
        private boolean allowedVanilla;
        private boolean finishedApplied;
        private final Cake cake;

        private CakeAccess(GameMode gameMode, int foodLevel, float saturation, int bites) {
            this.gameMode = gameMode;
            this.foodLevel = foodLevel;
            this.saturation = saturation;
            this.bites = bites;
            this.cake = proxy(Cake.class, this::handleCake);
        }

        @Override
        public GameMode gameMode() {
            return gameMode;
        }

        @Override
        public int foodLevel() {
            return foodLevel;
        }

        @Override
        public void foodLevel(int foodLevel) {
            this.foodLevel = foodLevel;
        }

        @Override
        public float saturation() {
            return saturation;
        }

        @Override
        public void saturation(float saturation) {
            this.saturation = saturation;
        }

        @Override
        public Cake cake() {
            return cake;
        }

        @Override
        public void updateCake(Cake cake) {
            updatedCake = true;
        }

        @Override
        public void removeCake() {
            removedCake = true;
        }

        @Override
        public void playEatSound() {
            playedSound = true;
        }

        @Override
        public void allowVanilla() {
            allowedVanilla = true;
        }

        @Override
        public void finishApplied() {
            finishedApplied = true;
        }

        private Object handleCake(Object target, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getBites" -> bites;
                case "setBites" -> {
                    bites = (Integer) args[0];
                    yield null;
                }
                case "getMaximumBites" -> 6;
                case "getMaterial" -> Material.CAKE;
                default -> defaultValue(target, method, args);
            };
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Object target, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> "CakeProxy";
                case "hashCode" -> System.identityHashCode(target);
                case "equals" -> target == args[0];
                default -> null;
            };
        }
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == float.class) {
            return 0.0F;
        }
        if (returnType == double.class) {
            return 0.0D;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}

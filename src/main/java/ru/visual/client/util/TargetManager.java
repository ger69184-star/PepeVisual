package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Текущая цель: на кого смотрим или кого ударили в последние секунды. */
public final class TargetManager {
    private static LivingEntity target;
    private static long lastSeen;

    private TargetManager() {}

    public static void set(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e instanceof LivingEntity le && le != mc.player) {
            target = le;
            lastSeen = System.currentTimeMillis();
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            target = null;
            return;
        }
        if (mc.crosshairPickEntity instanceof LivingEntity le && le != mc.player) {
            target = le;
            lastSeen = System.currentTimeMillis();
        }
        if (target != null) {
            boolean gone = target.isRemoved() || !target.isAlive();
            boolean old = System.currentTimeMillis() - lastSeen > 6000;
            boolean far = mc.player.distanceTo(target) > 40;
            if (gone || old || far) target = null;
        }
    }

    public static LivingEntity get() {
        return target;
    }

    public static boolean isTarget(Entity e) {
        return target != null && target == e;
    }
}

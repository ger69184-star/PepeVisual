package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Цели клиента.
 * <ul>
 *     <li>{@link #get()} - "мягкая" цель для Target HUD: на кого смотришь или кого недавно ударил.</li>
 *     <li>{@link #hit()} - тот, кого ты ударил (для Target ESP / AutoPearl). Держится, пока ты продолжаешь бить.</li>
 * </ul>
 */
public final class TargetManager {
    private static LivingEntity target;
    private static long lastSeen;

    private static LivingEntity hitTarget;
    private static long hitTime;
    private static long firstHit;
    private static int hitCount;
    /** Сколько миллисекунд держится "цель по удару" после последнего удара (настраивается в Target ESP). */
    public static long hitHoldMs = 8000;

    private TargetManager() {}

    public static void set(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e instanceof LivingEntity le && le != mc.player) {
            target = le;
            lastSeen = System.currentTimeMillis();
        }
    }

    /** Ты ударил существо. */
    public static void hit(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (!(e instanceof LivingEntity le) || le == mc.player) return;
        long now = System.currentTimeMillis();
        if (hitTarget != le) {
            hitTarget = le;
            firstHit = now;
            hitCount = 0;
        }
        hitCount++;
        hitTime = now;
        set(e);
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            target = null;
            hitTarget = null;
            return;
        }
        if (mc.crosshairPickEntity instanceof LivingEntity le && le != mc.player) {
            target = le;
            lastSeen = System.currentTimeMillis();
        }
        long now = System.currentTimeMillis();
        if (target != null) {
            boolean gone = target.isRemoved() || !target.isAlive();
            boolean old = now - lastSeen > 6000;
            boolean far = mc.player.distanceTo(target) > 40;
            if (gone || old || far) target = null;
        }
        if (hitTarget != null) {
            boolean gone = hitTarget.isRemoved() || !hitTarget.isAlive() || hitTarget.level() != mc.level;
            boolean old = now - hitTime > hitHoldMs;
            boolean far = mc.player.distanceTo(hitTarget) > 96;
            if (gone || old || far) hitTarget = null;
        }
    }

    public static LivingEntity get() {
        return target;
    }

    public static boolean isTarget(Entity e) {
        return target != null && target == e;
    }

    /** Цель по удару или null. */
    public static LivingEntity hit() {
        return hitTarget;
    }

    public static boolean isHitTarget(Entity e) {
        return hitTarget != null && hitTarget == e;
    }

    /** Миллисекунд с последнего удара по цели. */
    public static long sinceHit() {
        return hitTarget == null ? Long.MAX_VALUE : System.currentTimeMillis() - hitTime;
    }

    /** Миллисекунд с первого удара по текущей цели. */
    public static long sinceFirstHit() {
        return hitTarget == null ? Long.MAX_VALUE : System.currentTimeMillis() - firstHit;
    }

    public static int hitCount() {
        return hitTarget == null ? 0 : hitCount;
    }

    /** Насколько "свежа" цель: 1 сразу после удара, плавно гаснет к 0 в конце времени удержания. */
    public static float hitFade() {
        if (hitTarget == null) return 0f;
        long s = sinceHit();
        long fadeStart = Math.max(0, hitHoldMs - 1500);
        if (s <= fadeStart) return 1f;
        return Math.max(0f, 1f - (float) (s - fadeStart) / 1500f);
    }

    public static void clearHit() {
        hitTarget = null;
    }
}

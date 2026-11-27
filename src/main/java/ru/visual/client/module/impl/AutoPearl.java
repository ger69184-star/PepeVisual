package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;
import ru.visual.client.util.R3D;
import ru.visual.client.util.TargetManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Жемчуг за целью. Работает в паре с Target ESP: когда враг, которого ты ударил, убегает дальше заданной
 * дистанции, бросает жемчуг эндера так, чтобы он упал рядом с ним (с упреждением по скорости врага).
 */
public class AutoPearl extends Module {
    private final ModeSetting mode = add(new ModeSetting("Режим", "Авто - бросает сам, когда цель убегает. По клавише - включил функцию = один бросок за целью.",
            "Авто", "Авто", "По клавише (один бросок)"));
    private final NumSetting minDist = add(new NumSetting("Мин. дистанция", "Бросать, только если цель дальше этого расстояния (блоки).", 12, 5, 40, 1));
    private final NumSetting maxDist = add(new NumSetting("Макс. дистанция", "Не бросать, если цель дальше (жемчуг улетает примерно на 40-50 блоков).", 45, 15, 80, 1));
    private final BoolSetting fleeing = add(new BoolSetting("Только если убегает", "Авто-режим: бросать, только когда расстояние до цели растёт.", true));
    private final NumSetting interval = add(new NumSetting("Пауза (сек)", "Минимальная пауза между авто-бросками.", 3, 1, 15, 0.5));
    private final NumSetting accuracy = add(new NumSetting("Точность (блоки)", "Насколько далеко от цели может упасть жемчуг, чтобы бросок считался удачным.", 3, 1, 8, 0.5));
    private final BoolSetting lead = add(new BoolSetting("Упреждение", "Целиться туда, где враг окажется, когда жемчуг долетит.", true));
    private final BoolSetting restoreView = add(new BoolSetting("Вернуть взгляд", "После броска вернуть камеру туда, куда ты смотрел.", true));
    private final BoolSetting preview = add(new BoolSetting("Показывать траекторию", "Рисовать путь будущего броска и точку падения.", true));
    private final BoolSetting messages = add(new BoolSetting("Сообщения", "Писать над хотбаром, почему бросок не получился.", true));

    private record Solution(float yaw, float pitch, Vec3 landing, Vec3 aimPoint, List<Vec3> path, double miss, int ticks) {}

    private Vec3 lastTargetPos;
    private Vec3 targetVel = Vec3.ZERO;
    private LivingEntity tracked;
    private final double[] distHistory = new double[10];
    private int histIdx;
    private long tick, lastThrow = -1000;
    private Solution cached;
    private int pearls;

    public AutoPearl() {
        super("AutoPearl", "Жемчуг за целью: вместе с Target ESP бросает жемчуг эндера туда, куда убегает ударенный враг.", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return mode.is("Авто") ? Lang.t("авто") : Lang.t("клавиша");
    }

    @Override
    protected void onDisable() {
        cached = null;
        tracked = null;
        lastTargetPos = null;
    }

    private LivingEntity target() {
        return TargetManager.hit();
    }

    @Override
    public void onTick(Minecraft mc) {
        tick++;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) return;
        LivingEntity t = target();
        pearls = countPearls(p);

        // скорость цели считаем сами: у чужих игроков getDeltaMovement на клиенте почти всегда 0
        if (t != tracked) {
            tracked = t;
            lastTargetPos = t == null ? null : t.position();
            targetVel = Vec3.ZERO;
            java.util.Arrays.fill(distHistory, -1);
        } else if (t != null) {
            Vec3 now = t.position();
            Vec3 v = now.subtract(lastTargetPos);
            if (v.lengthSqr() > 9) v = Vec3.ZERO; // телепорт
            targetVel = targetVel.scale(0.6).add(v.scale(0.4));
            lastTargetPos = now;
        }
        if (t != null) {
            distHistory[histIdx] = p.distanceTo(t);
            histIdx = (histIdx + 1) % distHistory.length;
        }

        boolean keyMode = mode.is("По клавише (один бросок)");
        if (t == null) {
            cached = null;
            if (keyMode) {
                say(p, Lang.t("AutoPearl: нет цели - сначала ударь врага"));
                setEnabled(false);
            }
            return;
        }
        if (preview.get() && tick % 3 == 0) cached = solve(mc, p, t);

        if (mc.screen != null || p.isUsingItem()) return;

        double dist = p.distanceTo(t);
        if (keyMode) {
            tryThrow(mc, p, t, true);
            setEnabled(false);
            return;
        }
        if (tick - lastThrow < interval.get() * 20) return;
        if (dist < minDist.get() || dist > maxDist.get()) return;
        if (fleeing.get() && !isFleeing(dist)) return;
        tryThrow(mc, p, t, false);
    }

    private boolean isFleeing(double dist) {
        double old = distHistory[histIdx]; // самое старое значение в кольцевом буфере
        return old >= 0 && dist - old > 1.2;
    }

    private int countPearls(LocalPlayer p) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.ENDER_PEARL)) n += s.getCount();
        }
        return n;
    }

    private void tryThrow(Minecraft mc, LocalPlayer p, LivingEntity t, boolean verbose) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        Inventory inv = p.getInventory();
        int slot = -1;
        ItemStack stack;
        if (p.getOffhandItem().is(Items.ENDER_PEARL)) {
            hand = InteractionHand.OFF_HAND;
            stack = p.getOffhandItem();
        } else {
            for (int i = 0; i < 9; i++) {
                if (inv.getItem(i).is(Items.ENDER_PEARL)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                if (verbose || tick % 100 == 0) say(p, Lang.t("AutoPearl: нет жемчуга в хотбаре"));
                lastThrow = tick;
                return;
            }
            stack = inv.getItem(slot);
        }
        if (p.getCooldowns().getCooldownPercent(stack, 0f) > 0f) return;

        Solution s = solve(mc, p, t);
        if (s == null || s.miss > accuracy.get()) {
            if (verbose) say(p, Lang.t("AutoPearl: не долететь до цели (мешают блоки или слишком далеко)"));
            lastThrow = tick - (long) (interval.get() * 20) + 10; // повторим через полсекунды
            return;
        }

        int prev = inv.getSelectedSlot();
        float oldYaw = p.getYRot(), oldPitch = p.getXRot();
        if (hand == InteractionHand.MAIN_HAND && slot != prev) inv.setSelectedSlot(slot);
        p.setYRot(s.yaw);
        p.setXRot(s.pitch);
        mc.gameMode.useItem(p, hand);
        p.swing(hand);
        if (restoreView.get()) {
            p.setYRot(oldYaw);
            p.setXRot(oldPitch);
        }
        if (hand == InteractionHand.MAIN_HAND && slot != prev) inv.setSelectedSlot(prev);
        lastThrow = tick;
        cached = null;
    }

    private void say(LocalPlayer p, String s) {
        if (messages.get()) p.displayClientMessage(Component.literal(s), true);
    }

    // ---------- баллистика ----------

    private Solution solve(Minecraft mc, LocalPlayer p, LivingEntity t) {
        Vec3 start = p.getEyePosition().subtract(0, 0.1, 0);
        Vec3 mv = p.getDeltaMovement();
        Vec3 inherit = new Vec3(mv.x, p.onGround() ? 0 : mv.y, mv.z);
        Vec3 aim = t.position();
        Solution best = null;
        for (int iter = 0; iter < 3; iter++) {
            best = solveFor(mc, p, start, inherit, aim);
            if (best == null || !lead.get()) break;
            Vec3 predicted = t.position().add(targetVel.x * best.ticks, 0, targetVel.z * best.ticks);
            if (predicted.distanceToSqr(aim) < 0.25) break;
            aim = predicted;
        }
        return best;
    }

    private Solution solveFor(Minecraft mc, LocalPlayer p, Vec3 start, Vec3 inherit, Vec3 aim) {
        double dx = aim.x - start.x, dz = aim.z - start.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        Solution best = null;
        // грубый перебор угла, потом уточнение вокруг лучшего
        for (float pitch = -70f; pitch <= 40f; pitch += 2f) {
            Solution s = simulate(mc, p, start, inherit, yaw, pitch, aim, false);
            if (s != null && better(s, best)) best = s;
        }
        if (best == null) return null;
        float center = best.pitch;
        for (float pitch = center - 2f; pitch <= center + 2f; pitch += 0.25f) {
            Solution s = simulate(mc, p, start, inherit, yaw, pitch, aim, false);
            if (s != null && better(s, best)) best = s;
        }
        return simulate(mc, p, start, inherit, yaw, best.pitch, aim, true);
    }

    /** Лучше - меньше промах; при почти одинаковом промахе - быстрее долетает (настильная траектория). */
    private boolean better(Solution a, Solution b) {
        if (b == null) return true;
        double tol = accuracy.get() * 0.5;
        if (a.miss <= tol && b.miss <= tol) return a.ticks < b.ticks;
        return a.miss < b.miss;
    }

    private Solution simulate(Minecraft mc, LocalPlayer p, Vec3 start, Vec3 inherit, float yaw, float pitch, Vec3 aim, boolean keepPath) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3 vel = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)).scale(1.5).add(inherit);
        Vec3 pos = start;
        List<Vec3> path = keepPath ? new ArrayList<>() : null;
        if (path != null) path.add(pos);
        for (int i = 0; i < 160; i++) {
            Vec3 next = pos.add(vel);
            BlockHitResult r = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (r.getType() != HitResult.Type.MISS) {
                Vec3 land = r.getLocation();
                if (path != null) path.add(land);
                double hx = land.x - aim.x, hz = land.z - aim.z, hy = (land.y - aim.y) * 0.5;
                return new Solution(yaw, pitch, land, aim, path, Math.sqrt(hx * hx + hz * hz + hy * hy), i + 1);
            }
            pos = next;
            if (path != null) path.add(pos);
            boolean water = mc.level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
            vel = vel.scale(water ? 0.8 : 0.99).add(0, -0.03, 0);
            if (pos.y < aim.y - 30) return null;
        }
        return null;
    }

    // ---------- отрисовка ----------

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        Solution s = cached;
        if (!preview.get() || s == null || s.path == null || target() == null) return;
        boolean good = s.miss <= accuracy.get();
        int c = good ? 0xFFB070FF : 0xFFFF5050;
        for (int i = 1; i < s.path.size(); i++) {
            int a = (int) Mth.clamp(60 + i * 8, 60, 220);
            R3D.line(s.path.get(i - 1), s.path.get(i), Draw.withAlpha(c, a), 2f);
        }
        Vec3 l = s.landing;
        R3D.circle(l.x, l.y + 0.02, l.z, 0.5, 24, c, 2.5f);
        R3D.circle(s.aimPoint.x, s.aimPoint.y + 0.05, s.aimPoint.z, accuracy.get(), 32, Draw.withAlpha(0xFFFFFF, 90), 1.5f);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        LivingEntity t = target();
        if (t == null && !mode.is("По клавише (один бросок)")) return; // без цели не мусорим под прицелом
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        String state;
        int col;
        if (t == null) {
            state = Lang.t("ударь врага");
            col = Draw.GRAY;
        } else if (pearls == 0) {
            state = Lang.t("нет жемчуга");
            col = 0xFFFF6A6A;
        } else {
            double d = mc.player.distanceTo(t);
            boolean cd = mc.player.getCooldowns().getCooldownPercent(new ItemStack(Items.ENDER_PEARL), 0f) > 0f;
            if (cd) { state = Lang.t("перезарядка"); col = 0xFFFFC040; }
            else if (d < minDist.get()) { state = String.format(Lang.t("цель рядом (%.0fм)"), d); col = Draw.GRAY; }
            else if (d > maxDist.get()) { state = String.format(Lang.t("слишком далеко (%.0fм)"), d); col = 0xFFFF6A6A; }
            else { state = String.format(Lang.t("готов (%.0fм)"), d); col = 0xFF7CFFB0; }
        }
        String s = Lang.t("Жемчуг x") + pearls + ": " + state;
        int w = Draw.width(s) + 8;
        int x = sw / 2 - w / 2, y = sh / 2 + 18;
        Draw.panel(g, x, y, x + w, y + 12, Draw.bg());
        Draw.text(g, s, x + 4, y + 2, col);
        if (cached != null && preview.get() && t != null) {
            double[] o = new double[3];
            if (Projection.project(cached.landing.x, cached.landing.y, cached.landing.z, o)) {
                Draw.textCentered(g, String.format(Lang.t("%.1f бл. от цели"), cached.miss), (int) o[0], (int) o[1] - 12,
                        cached.miss <= accuracy.get() ? 0xFFB070FF : 0xFFFF5050);
            }
        }
    }
}

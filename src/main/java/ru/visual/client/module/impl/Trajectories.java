package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;

import java.util.ArrayList;
import java.util.List;

public class Trajectories extends Module implements Config.DefaultOn {
    private final BoolSetting mine = add(new BoolSetting("Мои броски", "Траектория предмета в твоей руке: жемчуг эндера, снежок, яйцо, зелье, лук, арбалет, трезубец.", true));
    private final BoolSetting others = add(new BoolSetting("Чужие снаряды", "Траектория и точка приземления снарядов в полёте, в том числе чужих жемчугов эндера.", true));
    private final BoolSetting landing = add(new BoolSetting("Точка падения", "Маркер и подпись (дистанция, время) в точке приземления.", true));

    private record Params(double speed, double gravity, double pitchOffset, String label) {}

    private static class Sim {
        final List<Vec3> pts = new ArrayList<>();
        Vec3 end;
        boolean hit;
        int steps;
    }

    public Trajectories() {
        super("Trajectories", "Показывает путь полёта и место падения жемчуга эндера и других снарядов.", Category.VISUAL);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        LocalPlayer p = mc.player;

        if (mine.get()) {
            Params prm = paramsFor(mc, p.getMainHandItem());
            if (prm == null) prm = paramsFor(mc, p.getOffhandItem());
            if (prm != null) {
                double yaw = Math.toRadians(p.getViewYRot(pt));
                double pitch = Math.toRadians(p.getViewXRot(pt));
                Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch),
                        -Math.sin(pitch + Math.toRadians(prm.pitchOffset)),
                        Math.cos(yaw) * Math.cos(pitch)).normalize();
                Vec3 mv = p.getDeltaMovement();
                Vec3 vel = dir.scale(prm.speed).add(mv.x, p.onGround() ? 0 : mv.y, mv.z);
                Vec3 start = p.getEyePosition(pt).subtract(0, 0.1, 0);
                Sim sim = simulate(mc, start, vel, prm.gravity, p);
                draw(g, mc, sim, 0xFF7CFFB0, prm.label, null);
            }
        }

        if (others.get()) {
            int n = 0;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e.isRemoved()) continue;
                double gravity;
                String label;
                EntityType<?> t = e.getType();
                if (t == EntityType.ENDER_PEARL) { gravity = 0.03; label = Lang.t("Жемчуг"); }
                else if (t == EntityType.SNOWBALL) { gravity = 0.03; label = Lang.t("Снежок"); }
                else if (t == EntityType.EGG) { gravity = 0.03; label = Lang.t("Яйцо"); }
                else if (t == EntityType.ARROW || t == EntityType.SPECTRAL_ARROW) { gravity = 0.05; label = Lang.t("Стрела"); }
                else if (t == EntityType.TRIDENT) { gravity = 0.05; label = Lang.t("Трезубец"); }
                else continue;
                Vec3 v = e.getDeltaMovement();
                if (v.lengthSqr() < 0.02) continue; // стоит / воткнулась
                if (++n > 8) break;
                String owner = null;
                if (e instanceof Projectile pr && pr.getOwner() != null) owner = pr.getOwner().getName().getString();
                Sim sim = simulate(mc, e.getPosition(pt), v, gravity, e);
                int color = t == EntityType.ENDER_PEARL ? 0xFFB070FF : 0xFFFFB060;
                draw(g, mc, sim, color, label, owner);
            }
        }
    }

    private Params paramsFor(Minecraft mc, ItemStack st) {
        if (st.isEmpty()) return null;
        if (st.is(Items.ENDER_PEARL)) return new Params(1.5, 0.03, 0, Lang.t("Жемчуг"));
        if (st.is(Items.SNOWBALL)) return new Params(1.5, 0.03, 0, Lang.t("Снежок"));
        if (st.is(Items.EGG)) return new Params(1.5, 0.03, 0, Lang.t("Яйцо"));
        if (st.is(Items.SPLASH_POTION) || st.is(Items.LINGERING_POTION)) return new Params(0.5, 0.05, -20, Lang.t("Зелье"));
        if (st.is(Items.EXPERIENCE_BOTTLE)) return new Params(0.7, 0.07, -20, Lang.t("Опыт"));
        if (st.is(Items.TRIDENT) && mc.player.isUsingItem()) return new Params(2.5, 0.05, 0, Lang.t("Трезубец"));
        if (st.is(Items.BOW) && mc.player.isUsingItem()) {
            float f = mc.player.getTicksUsingItem() / 20.0f;
            f = (f * f + f * 2.0f) / 3.0f;
            if (f < 0.1f) return null;
            if (f > 1f) f = 1f;
            return new Params(f * 3.0, 0.05, 0, Lang.t("Стрела"));
        }
        if (st.is(Items.CROSSBOW) && CrossbowItem.isCharged(st)) return new Params(3.15, 0.05, 0, Lang.t("Арбалет"));
        return null;
    }

    private Sim simulate(Minecraft mc, Vec3 pos, Vec3 vel, double gravity, Entity owner) {
        Sim s = new Sim();
        s.pts.add(pos);
        for (int i = 0; i < 200; i++) {
            Vec3 next = pos.add(vel);
            BlockHitResult r = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            s.steps = i + 1;
            if (r.getType() != HitResult.Type.MISS) {
                s.pts.add(r.getLocation());
                s.hit = true;
                break;
            }
            pos = next;
            s.pts.add(pos);
            boolean water = mc.level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
            vel = vel.scale(water ? 0.8 : 0.99).add(0, -gravity, 0);
            if (pos.y < mc.level.getMinY() - 8) break;
        }
        s.end = s.pts.get(s.pts.size() - 1);
        return s;
    }

    private void draw(GuiGraphics g, Minecraft mc, Sim sim, int color, String label, String owner) {
        double[] a = new double[3], b = new double[3];
        boolean haveA = false;
        for (Vec3 v : sim.pts) {
            boolean ok = Projection.project(v.x, v.y, v.z, b);
            if (ok && haveA) Draw.dottedLine(g, a[0], a[1], b[0], b[1], color, 3);
            if (ok) {
                a[0] = b[0];
                a[1] = b[1];
                haveA = true;
            } else {
                haveA = false;
            }
        }
        if (!landing.get() || !sim.hit) return;
        Vec3 e = sim.end;
        if (!Projection.project(e.x, e.y, e.z, b)) return;
        double dist = mc.player.position().distanceTo(e);
        boolean danger = dist < 5.0;
        int c = danger ? 0xFFFF4040 : color;
        int x = (int) b[0], y = (int) b[1];
        Draw.outline(g, x - 4, y - 4, x + 5, y + 5, c);
        Draw.dot(g, x, y, 2, c);
        String text = label + (owner != null ? " (" + owner + ")" : "") + " " + String.format(java.util.Locale.ROOT, "%.1f", dist)
                + Lang.t("м ") + String.format(java.util.Locale.ROOT, "%.1f", sim.steps / 20.0) + Lang.t("с");
        int tw = Draw.width(text);
        Draw.rect(g, x - tw / 2 - 2, y + 7, x + tw / 2 + 2, y + 18, 0x90000000);
        Draw.text(g, text, x - tw / 2, y + 9, c);
    }
}

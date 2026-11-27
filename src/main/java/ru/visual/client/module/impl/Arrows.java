package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;
import ru.visual.client.util.TargetManager;

/** Стрелки вокруг прицела, указывающие на игроков (и мобов), - видно, откуда к тебе подходят. */
public class Arrows extends Module {
    private final BoolSetting players = add(new BoolSetting("Игроки", "Стрелки на игроков.", true));
    private final BoolSetting mobs = add(new BoolSetting("Враждебные мобы", "Стрелки на враждебных мобов.", false));
    private final BoolSetting offscreen = add(new BoolSetting("Только вне экрана", "Не показывать стрелку, если существо и так видно на экране.", true));
    private final BoolSetting names = add(new BoolSetting("Подписи", "Ник и расстояние у стрелки.", false));
    private final NumSetting radius = add(new NumSetting("Радиус", "Расстояние стрелок от прицела в пикселях.", 45, 20, 150, 1));
    private final NumSetting size = add(new NumSetting("Размер", "Размер стрелки.", 6, 3, 12, 1));
    private final NumSetting range = add(new NumSetting("Дистанция", "Максимальное расстояние до существа (блоки).", 64, 8, 256, 8));
    private final ColorSetting color = add(new ColorSetting("Цвет", "Цвет стрелок на игроков (цель Target ESP всегда красная).", 0xFFFFFF));

    public Arrows() {
        super("Arrows", "Стрелки вокруг прицела указывают направление на игроков и мобов, даже если они за спиной.", Category.VISUAL);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (mc.options.hideGui) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        float cx = sw / 2f, cy = sh / 2f;
        Vec3 me = mc.player.getPosition(pt);
        float yaw = (float) Math.toRadians(mc.player.getViewYRot(pt));
        double max = range.get();
        double[] o = new double[3];
        int drawn = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive()) continue;
            boolean isPlayer = e instanceof Player;
            if (isPlayer ? !players.get() : !(mobs.get() && e instanceof Enemy)) continue;
            if (isPlayer && ((Player) e).isSpectator()) continue;
            Vec3 p = e.getPosition(pt);
            double dist = p.distanceTo(me);
            if (dist > max) continue;
            if (offscreen.get() && Projection.project(p.x, p.y + e.getBbHeight() * 0.5, p.z, o)
                    && Projection.onScreen(o[0], o[1], sw, sh)) continue;
            if (++drawn > 40) break;

            double dx = p.x - me.x, dz = p.z - me.z;
            // вперёд = (-sin yaw, cos yaw), вправо = (-cos yaw, -sin yaw)
            double fwd = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
            double right = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
            float theta = (float) Math.atan2(right, fwd);
            float r = radius.asFloat();
            float ax = cx + Mth.sin(theta) * r, ay = cy - Mth.cos(theta) * r;

            int base;
            if (TargetManager.isHitTarget(e)) base = 0xFFFF3B3B;
            else if (isPlayer) base = color.argb() | 0xFF000000;
            else base = 0xFFFF9A3B;
            int alpha = (int) Mth.clamp(255 - dist / max * 150, 90, 255);
            int c = Draw.withAlpha(base, alpha);

            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(ax, ay);
            pose.rotate(theta);
            int s = size.asInt();
            // треугольник остриём вверх (наружу от прицела) из горизонтальных полос
            for (int row = 0; row < s; row++) {
                int half = row * 3 / 4;
                g.fill(-half - 1, row - s / 2, half + 1, row - s / 2 + 1, c);
            }
            pose.popMatrix();

            if (names.get()) {
                String label = (isPlayer ? e.getName().getString() + " " : "") + (int) dist + Lang.t("м");
                float lr = r + s + 8;
                int lx = (int) (cx + Mth.sin(theta) * lr), ly = (int) (cy - Mth.cos(theta) * lr);
                Draw.textCentered(g, label, lx, ly - 4, c);
            }
        }
    }
}

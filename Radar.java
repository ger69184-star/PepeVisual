package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.TargetManager;

public class Radar extends HudModule {
    private final NumSetting size = add(new NumSetting("Размер", "Размер радара в пикселях.", 80, 50, 160, 5));
    private final NumSetting range = add(new NumSetting("Дальность", "Сколько блоков вокруг тебя показывает радар.", 48, 16, 160, 8));
    private final BoolSetting mobs = add(new BoolSetting("Мобы", "Показывать на радаре и мобов (жёлтые точки).", false));

    public Radar() {
        super("Radar", "Мини-радар: игроки вокруг тебя точками, вверх - направление взгляда.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int s = size.asInt();
        int x = 4 + ox(), y = 92 + oy();
        int cx = x + s / 2, cy = y + s / 2;
        Draw.rect(g, x, y, x + s, y + s, Draw.bg());
        Draw.outline(g, x, y, x + s, y + s, Draw.withAlpha(Draw.accent(), 160));
        Draw.rect(g, cx, y + 1, cx + 1, y + s - 1, 0x30FFFFFF);
        Draw.rect(g, x + 1, cy, x + s - 1, cy + 1, 0x30FFFFFF);

        double yaw = Math.toRadians(mc.player.getViewYRot(pt));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double half = s / 2.0 - 3;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || e.isRemoved() || !(e instanceof LivingEntity)) continue;
            boolean player = e instanceof Player;
            if (!player && !(mobs.get() && e.getType() != EntityType.ARMOR_STAND)) continue;
            double dx = e.getX() - mc.player.getX(), dz = e.getZ() - mc.player.getZ();
            double px = (dx * rx + dz * rz) / range.get() * half;
            double py = (dx * fx + dz * fz) / range.get() * half;
            px = Math.max(-half, Math.min(half, px));
            py = Math.max(-half, Math.min(half, py));
            int color = TargetManager.isTarget(e) ? 0xFFFF3030 : (player ? 0xFFFF6A6A : 0xFFFFD84A);
            Draw.dot(g, cx + px, cy - py, 3, color);
        }
        Draw.dot(g, cx, cy, 3, 0xFFFFFFFF);
    }
}

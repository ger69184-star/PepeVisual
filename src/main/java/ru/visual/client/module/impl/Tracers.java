package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;
import ru.visual.client.util.TargetManager;

public class Tracers extends Module {
    private final NumSetting range = add(new NumSetting("Дистанция", "До каких игроков рисуются линии (блоки).", 64, 8, 256, 8));

    public Tracers() {
        super("Tracers", "Пунктирные линии от центра экрана к игрокам - показывают, где они находятся.", Category.VISUAL);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        double[] o = new double[3];
        int n = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Player p) || p == mc.player || p.isRemoved()) continue;
            if (mc.player.distanceTo(p) > range.get()) continue;
            if (++n > 30) break;
            Vec3 pos = p.getPosition(pt);
            if (!Projection.project(pos.x, pos.y + p.getBbHeight() * 0.5, pos.z, o)) continue;
            int color = TargetManager.isTarget(p) ? 0xFFFF4040 : 0xCCFFFFFF;
            Draw.dottedLine(g, w / 2.0, h / 2.0 + 10, o[0], o[1], color, 4);
        }
    }
}

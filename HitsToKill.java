package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.HitCalc;
import ru.visual.client.util.Projection;
import ru.visual.client.util.TargetManager;

import java.util.Locale;

public class HitsToKill extends Module implements Config.DefaultOn {
    private final BoolSetting above = add(new BoolSetting("Над целью", "Показывать расчёт над головой цели.", true));
    private final BoolSetting crosshair = add(new BoolSetting("Под прицелом", "Показывать расчёт под прицелом.", true));
    private final BoolSetting detail = add(new BoolSetting("Подробно", "Добавлять урон за удар и здоровье цели.", false));

    public HitsToKill() {
        super("HitsToKill", "Считает, сколько ударов осталось до смерти цели (по оружию в руке, броне, зачарованиям и сопротивлению). Оценка примерная.", Category.VISUAL);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        LivingEntity t = TargetManager.get();
        if (t == null) return;
        HitCalc.Result r = HitCalc.calc(mc, t);
        int color = r.hits() <= 2 ? 0xFFFF5050 : (r.hits() <= 5 ? 0xFFFFD84A : 0xFF7CFFB0);
        String text = "До смерти: " + r.hits() + " уд. (крит: " + r.critHits() + ")";
        if (detail.get()) {
            text += String.format(Locale.ROOT, "  [%.1f урона, %.1f HP]", r.damage(), r.hp());
        }

        if (above.get()) {
            float pt = delta.getGameTimeDeltaPartialTick(false);
            Vec3 p = t.getPosition(pt);
            double[] o = new double[3];
            if (Projection.project(p.x, p.y + t.getBbHeight() + 0.55, p.z, o)) {
                int tw = Draw.width(text);
                Draw.rect(g, (int) o[0] - tw / 2 - 3, (int) o[1] - 2, (int) o[0] + tw / 2 + 3, (int) o[1] + 10, 0xA0000000);
                Draw.textCentered(g, text, (int) o[0], (int) o[1], color);
            }
        }
        if (crosshair.get()) {
            int cx = mc.getWindow().getGuiScaledWidth() / 2;
            int cy = mc.getWindow().getGuiScaledHeight() / 2 + 14;
            int tw = Draw.width(text);
            Draw.rect(g, cx - tw / 2 - 3, cy - 2, cx + tw / 2 + 3, cy + 10, 0xA0000000);
            Draw.textCentered(g, text, cx, cy, color);
        }
    }
}

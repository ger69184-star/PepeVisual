package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.util.Draw;

import java.util.ArrayList;
import java.util.List;

public class PotionHud extends HudModule implements Config.DefaultOn {
    public PotionHud() {
        super("PotionHUD", "Список активных эффектов с оставшимся временем в правом нижнем углу.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        List<MobEffectInstance> list = new ArrayList<>(mc.player.getActiveEffects());
        if (list.isEmpty()) return;
        int w = mc.getWindow().getGuiScaledWidth() + ox();
        int y = mc.getWindow().getGuiScaledHeight() - 6 - list.size() * 12 + oy();
        for (MobEffectInstance e : list) {
            String name = e.getEffect().value().getDisplayName().getString();
            if (e.getAmplifier() > 0) name += " " + (e.getAmplifier() + 1);
            String time;
            if (e.isInfiniteDuration()) {
                time = "∞";
            } else {
                int sec = e.getDuration() / 20;
                time = (sec / 60) + ":" + String.format("%02d", sec % 60);
            }
            String s = name + "  " + time;
            int color = e.getEffect().value().isBeneficial() ? 0xFF7CFFB0 : 0xFFFF6060;
            int tw = Draw.width(s);
            Draw.rect(g, w - tw - 8, y - 1, w - 3, y + 10, 0x80000000);
            Draw.text(g, s, w - tw - 5, y, color);
            y += 12;
        }
    }
}

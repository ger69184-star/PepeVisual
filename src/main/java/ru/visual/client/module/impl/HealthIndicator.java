package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;

import java.util.Locale;

/** Здоровье существа под прицелом - маленькая полоска прямо под перекрестием. */
public class HealthIndicator extends HudModule {
    private final BoolSetting name = add(new BoolSetting("Имя", "Показывать имя существа над полоской.", true));
    private final BoolSetting self = add(new BoolSetting("Своё здоровье", "Если никого нет под прицелом - показывать своё HP.", false));

    private float shown;
    private Entity last;

    public HealthIndicator() {
        super("Health Indicator", "Полоска здоровья существа, на которое ты смотришь, прямо под прицелом.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        LivingEntity t = mc.crosshairPickEntity instanceof LivingEntity le && le.isAlive() ? le : null;
        if (t == null && self.get()) t = mc.player;
        if (t == null || mc.options.hideGui) {
            last = null;
            return;
        }
        float hp = t.getHealth(), max = Math.max(1f, t.getMaxHealth()), abs = t.getAbsorptionAmount();
        if (t != last) {
            last = t;
            shown = hp;
        }
        shown += (hp - shown) * 0.2f;

        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int cx = w / 2 + ox(), y = h / 2 + 12 + oy();
        int bw = 50;
        if (name.get()) {
            String n = t.getName().getString();
            Draw.textCentered(g, n, cx, y, 0xFFFFFFFF);
            y += 10;
        }
        Draw.rect(g, cx - bw / 2 - 1, y - 1, cx + bw / 2 + 1, y + 4, 0xA0000000);
        float f = Math.max(0f, Math.min(1f, shown / max));
        Draw.rect(g, cx - bw / 2, y, cx - bw / 2 + (int) (bw * f), y + 3, Draw.healthColor(shown, max));
        if (abs > 0) {
            float fa = Math.max(0f, Math.min(1f, abs / max));
            Draw.rect(g, cx - bw / 2, y + 2, cx - bw / 2 + (int) (bw * fa), y + 3, 0xFFFFD700);
        }
        String txt = String.format(Locale.ROOT, abs > 0 ? "%.1f §e+%.1f" : "%.1f", hp, abs);
        Draw.textCentered(g, txt, cx, y + 6, Draw.healthColor(hp, max));
    }
}

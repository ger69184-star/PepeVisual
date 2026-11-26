package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.food.FoodData;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;

import java.util.Locale;

/**
 * Скрытое насыщение (saturation): сколько ещё голод не будет падать.
 * Раньше была длинная строка "Голод 20/20 Насыщение 17.0" прямо на хотбаре - теперь тонкая золотая полоска
 * над шкалой голода и маленькое число справа. Не налезает на хотбар и броню.
 */
public class HungerHud extends HudModule {
    private final BoolSetting number = add(new BoolSetting("Число", "Показывать значение насыщения числом рядом с полоской.", true));
    private final BoolSetting hideFull = add(new BoolSetting("Скрывать при сытости", "Прятать, когда голод 20 и насыщение не падает (ничего не происходит).", false));

    public HungerHud() {
        super("Saturation", "Скрытое насыщение: тонкая золотая полоска над шкалой голода и число.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (mc.player.isCreative() || mc.player.isSpectator() || mc.options.hideGui) return;
        FoodData f = mc.player.getFoodData();
        float sat = f.getSaturationLevel();
        if (hideFull.get() && f.getFoodLevel() >= 20 && sat >= 5f) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        // шкала голода: x от w/2+10 до w/2+91, верх на h-39
        int x2 = w / 2 + 91 + ox(), x1 = x2 - 81;
        int y = h - 42 + oy();
        float fr = Math.max(0f, Math.min(1f, sat / 20f));
        int col = sat <= 0 ? 0xFFFF6060 : 0xFFFFC94A;
        Draw.rect(g, x1, y, x2, y + 2, 0x70000000);
        if (fr > 0) Draw.rect(g, x2 - Math.max(1, (int) (81 * fr)), y, x2, y + 2, col);
        if (number.get()) {
            String s = String.format(Locale.ROOT, "%.1f", sat);
            int tw = Draw.width(s);
            Draw.panel(g, x2 + 3, y - 4, x2 + tw + 9, y + 7, Draw.bg(), false);
            Draw.text(g, s, x2 + 6, y - 3, col);
        }
        bounds(x1, y - 4, x2 + Draw.width("00.0") + 9, y + 7);
    }
}

package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;

/** Содержимое инвентаря (3 ряда над хотбаром) прямо на экране. */
public class InventoryHud extends HudModule {
    private final BoolSetting hideEmpty = add(new BoolSetting("Скрывать, если пусто", "Не показывать панель, когда в инвентаре (без хотбара) ничего нет.", true));

    public InventoryHud() {
        super("InventoryHUD", "Показывает содержимое инвентаря на экране, не открывая его.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (mc.options.hideGui) return;
        var inv = mc.player.getInventory();
        boolean any = false;
        for (int i = 9; i < 36; i++) if (!inv.getItem(i).isEmpty()) { any = true; break; }
        if (!any && hideEmpty.get()) return;
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = 4 + ox(), y = sh / 2 - 30 + oy();
        int w = 9 * 18 + 6, h = 3 * 18 + 6;
        Draw.panel(g, x, y, x + w, y + h, Draw.bg());
        for (int i = 0; i < 27; i++) {
            int cx = x + 3 + (i % 9) * 18, cy = y + 3 + (i / 9) * 18;
            Draw.round(g, cx, cy, cx + 18 - 1, cy + 18 - 1, 2, 0x20FFFFFF);
            ItemStack st = inv.getItem(9 + i);
            if (!st.isEmpty()) Draw.item(g, st, cx, cy);
        }
    }
}

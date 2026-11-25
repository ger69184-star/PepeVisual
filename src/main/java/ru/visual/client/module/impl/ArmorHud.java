package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.util.Draw;

public class ArmorHud extends HudModule implements Config.DefaultOn {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public ArmorHud() {
        super("ArmorHUD", "Показывает твою броню и её прочность над хотбаром.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int x = w / 2 - 36 + ox();
        int y = h - 78 + oy();
        for (EquipmentSlot s : SLOTS) {
            ItemStack st = mc.player.getItemBySlot(s);
            if (!st.isEmpty()) {
                Draw.item(g, st, x, y);
                if (st.isDamageableItem() && st.getMaxDamage() > 0) {
                    int left = st.getMaxDamage() - st.getDamageValue();
                    int pct = (int) (100.0 * left / st.getMaxDamage());
                    Draw.textCentered(g, pct + "%", x + 8, y - 9, Draw.healthColor(pct, 100));
                }
            }
            x += 18;
        }
    }
}

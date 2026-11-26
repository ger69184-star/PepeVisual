package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.util.Draw;

import java.util.ArrayList;
import java.util.List;

/**
 * Броня над хотбаром. Стоит выше сердец/брони/голода (не налезает на хотбар).
 * Если у всех вещей одинаковая прочность (например, всё новое - 100%), показывается одно число на всю панель,
 * как только что-то поцарапано - проценты у каждой вещи отдельно.
 */
public class ArmorHud extends HudModule implements Config.DefaultOn {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final ModeSetting numbers = add(new ModeSetting("Числа", "Проценты прочности или оставшиеся единицы прочности.", "Проценты", "Проценты", "Единицы", "Нет"));
    private final BoolSetting hands = add(new BoolSetting("Предметы в руках", "Добавить к броне оружие/инструмент и вторую руку.", false));
    private final BoolSetting panel = add(new BoolSetting("Подложка", "Полупрозрачная плашка под иконками.", true));
    private final BoolSetting bars = add(new BoolSetting("Полоски", "Тонкая цветная полоска прочности под каждой вещью.", true));

    public ArmorHud() {
        super("ArmorHUD", "Твоя броня и её прочность над хотбаром. Всё целое - одно число \"100%\", что-то поцарапано - проценты у каждой вещи.");
    }

    private static int pct(ItemStack st) {
        return (int) Math.floor(100.0 * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage());
    }

    private String label(ItemStack st) {
        if (!st.isDamageableItem() || st.getMaxDamage() <= 0) return st.getCount() > 1 ? String.valueOf(st.getCount()) : null;
        return numbers.is("Единицы") ? String.valueOf(st.getMaxDamage() - st.getDamageValue()) : pct(st) + "%";
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        List<ItemStack> items = new ArrayList<>();
        boolean editor = mc.screen instanceof ru.visual.client.gui.HudEditorScreen;
        if (hands.get() && !mc.player.getMainHandItem().isEmpty()) items.add(mc.player.getMainHandItem());
        for (EquipmentSlot s : ARMOR) {
            ItemStack st = mc.player.getItemBySlot(s);
            if (!st.isEmpty()) items.add(st);
        }
        if (hands.get() && !mc.player.getOffhandItem().isEmpty()) items.add(mc.player.getOffhandItem());
        if (items.isEmpty()) {
            if (!editor) return;
            items.add(new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
        }

        int w = mc.getWindow().getGuiScaledWidth();
        int n = items.size();
        int step = 18;
        int rowW = n * step - 2;
        boolean showNums = !numbers.is("Нет");

        // одинаковые числа -> одно на всех (у неломаемых вещей своего числа нет, они не мешают)
        String common = null;
        boolean same = true;
        boolean anyDamageable = false;
        for (ItemStack st : items) {
            if (!st.isDamageableItem() || st.getMaxDamage() <= 0) continue;
            anyDamageable = true;
            String l = label(st);
            if (common == null) common = l;
            else if (!common.equals(l)) same = false;
        }
        boolean collapse = showNums && anyDamageable && same && (numbers.is("Проценты") || n == 1);

        int textH = showNums && !collapse ? 9 : 0;
        int sideW = collapse ? Draw.width(common) + 6 : 0;
        int totalW = rowW + sideW;
        int x = w / 2 - totalW / 2 + ox();
        int y = Math.min(mc.getWindow().getGuiScaledHeight() - 80, Draw.hudTop(mc) - 22 - textH) + oy();

        if (panel.get()) Draw.panel(g, x - 3, y - 3 - textH, x + totalW + 3, y + 19, Draw.bg(), false);
        bounds(x - 3, y - 3 - textH, x + totalW + 3, y + 19);
        for (ItemStack st : items) {
            Draw.item(g, st, x, y);
            boolean dmg = st.isDamageableItem() && st.getMaxDamage() > 0;
            if (dmg && bars.get()) {
                float f = (float) (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage();
                Draw.rect(g, x + 1, y + 16, x + 15, y + 17, 0x80000000);
                Draw.rect(g, x + 1, y + 16, x + 1 + Math.max(1, (int) (14 * f)), y + 17, Draw.healthColor(f * 100, 100));
            }
            if (showNums && !collapse && dmg) {
                Draw.textCentered(g, label(st), x + 8, y - 10, Draw.healthColor(pct(st), 100));
            }
            x += step;
        }
        if (collapse) {
            int c = numbers.is("Проценты") ? Draw.healthColor(Integer.parseInt(common.replace("%", "")), 100) : Draw.WHITE;
            Draw.text(g, common, x + 2, y + 4, c);
        }
    }
}

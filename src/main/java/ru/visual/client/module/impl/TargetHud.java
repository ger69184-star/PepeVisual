package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.TargetManager;

public class TargetHud extends HudModule implements Config.DefaultOn {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final BoolSetting items = add(new BoolSetting("Предметы цели", "Показывает предметы в руках и броню цели.", true));

    private LivingEntity last;
    private float shownHp;

    public TargetHud() {
        super("TargetHUD", "Панель с информацией о цели: ник, здоровье, дистанция, предмет в руке и броня.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        LivingEntity t = TargetManager.get();
        if (t == null) {
            last = null;
            return;
        }
        if (t != last) {
            last = t;
            shownHp = t.getHealth();
        }
        float hp = t.getHealth() + t.getAbsorptionAmount();
        float max = Math.max(1f, t.getMaxHealth());
        shownHp += (hp - shownHp) * 0.15f;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int x = w / 2 + 26 + ox(), y = h / 2 + 26 + oy();
        int pw = 150, ph = items.get() ? 62 : 40;

        Draw.rect(g, x, y, x + pw, y + ph, Draw.bg());
        Draw.rect(g, x, y, x + 2, y + ph, Draw.accent());

        Draw.text(g, t.getName().getString(), x + 8, y + 5, Draw.WHITE);
        String hpText = String.format(java.util.Locale.ROOT, "%.1f", hp);
        Draw.textRight(g, hpText + " HP", x + pw - 5, y + 5, Draw.healthColor(hp, max));

        int bx1 = x + 8, bx2 = x + pw - 6, by = y + 18;
        Draw.rect(g, bx1, by, bx2, by + 6, 0xFF202028);
        float f = Math.max(0f, Math.min(1f, shownHp / max));
        Draw.rect(g, bx1, by, bx1 + (int) ((bx2 - bx1) * f), by + 6, Draw.healthColor(shownHp, max));

        Draw.text(g, (int) mc.player.distanceTo(t) + " м", x + 8, y + 27, Draw.GRAY);

        if (items.get()) {
            int ix = x + 8;
            for (EquipmentSlot s : SLOTS) {
                ItemStack st = t.getItemBySlot(s);
                if (!st.isEmpty()) {
                    g.renderItem(st, ix, y + 41);
                    g.renderItemDecorations(mc.font, st, ix, y + 41);
                } else {
                    Draw.outline(g, ix, y + 41, ix + 16, y + 57, 0x40FFFFFF);
                }
                ix += 22;
            }
        }
    }
}

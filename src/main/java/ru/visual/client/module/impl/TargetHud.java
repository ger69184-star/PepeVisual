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
        float health = t.getHealth();
        float absorption = t.getAbsorptionAmount();
        float hp = health + absorption;
        float max = Math.max(1f, t.getMaxHealth());
        shownHp += (health - shownHp) * 0.15f;
        if (Math.abs(health - shownHp) < 0.01f) shownHp = health;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int x = w / 2 + 26 + ox(), y = h / 2 + 26 + oy();
        int pw = 150, ph = items.get() ? 62 : 40;

        bounds(x, y, x + pw, y + ph);
        Draw.panel(g, x, y, x + pw, y + ph, Draw.bg());
        Draw.round(g, x + 2, y + 4, x + 4, y + ph - 4, 1, Draw.accent());

        Draw.text(g, t.getName().getString(), x + 8, y + 5, Draw.WHITE);
        String hpText = String.format(java.util.Locale.ROOT, "%.1f", hp);
        Draw.textRight(g, hpText + " HP", x + pw - 5, y + 5, absorption > 0 ? 0xFFFFD700 : Draw.healthColor(health, max));

        int bx1 = x + 8, bx2 = x + pw - 6, by = y + 18;
        Draw.rect(g, bx1, by, bx2, by + 6, 0xFF202028);
        float f = Math.max(0f, Math.min(1f, shownHp / max));
        Draw.rect(g, bx1, by, bx1 + (int) ((bx2 - bx1) * f), by + 6, Draw.healthColor(shownHp, max));
        if (absorption > 0) {
            // Поглощение (золотые сердца) - жёлтая полоска поверх
            float fa = Math.max(0f, Math.min(1f, absorption / max));
            Draw.rect(g, bx1, by + 4, bx1 + (int) ((bx2 - bx1) * fa), by + 6, 0xFFFFD700);
        }

        Draw.text(g, (int) mc.player.distanceTo(t) + " м", x + 8, y + 27, Draw.GRAY);

        if (items.get()) {
            int ix = x + 8;
            for (EquipmentSlot s : SLOTS) {
                ItemStack st = t.getItemBySlot(s);
                if (!st.isEmpty()) {
                    Draw.item(g, st, ix, y + 41);
                } else {
                    Draw.outline(g, ix, y + 41, ix + 16, y + 57, 0x40FFFFFF);
                }
                ix += 22;
            }
        }
    }
}

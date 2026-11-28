package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.TargetManager;

import java.util.Locale;

/**
 * Панель цели. Не налезает на хотбар: если экран маленький (крупный масштаб GUI), поднимается выше полосок здоровья.
 * Плавно появляется/исчезает, здоровье анимируется, показывает "урон" последних ударов светлой полосой.
 */
public class TargetHud extends HudModule implements Config.DefaultOn {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final BoolSetting items = add(new BoolSetting("Предметы цели", "Показывает предметы в руках и броню цели.", true));
    private final BoolSetting head = add(new BoolSetting("Голова игрока", "Лицо скина слева от ника.", true));

    private LivingEntity last;
    private float shownHp, trailHp, anim;

    public TargetHud() {
        super("TargetHUD", "Панель с информацией о цели: ник, здоровье, дистанция, предмет в руке и броня.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        LivingEntity t = TargetManager.get();
        // в редакторе HUD показываем панель на себе, иначе её нельзя было передвинуть без цели
        if (t == null && mc.screen instanceof ru.visual.client.gui.HudEditorScreen) t = mc.player;
        anim += ((t != null ? 1f : 0f) - anim) * 0.25f;
        if (t == null) {
            if (anim < 0.05f || last == null || last.isRemoved()) {
                last = null;
                return;
            }
            t = last; // плавно гасим последнюю цель
        }
        if (t != last) {
            last = t;
            shownHp = trailHp = t.getHealth();
        }
        float health = t.getHealth();
        float absorption = t.getAbsorptionAmount();
        float hp = health + absorption;
        float max = Math.max(1f, t.getMaxHealth());
        shownHp += (health - shownHp) * 0.2f;
        if (Math.abs(health - shownHp) < 0.01f) shownHp = health;
        // "след" урона догоняет медленнее - видно, сколько сняли последним ударом
        trailHp += (shownHp - trailHp) * (trailHp > shownHp ? 0.05f : 1f);

        int w = mc.getWindow().getGuiScaledWidth();
        boolean face = head.get() && t instanceof AbstractClientPlayer;
        int pw = 150, ph = items.get() ? 60 : 38;
        int x = w / 2 + 26 + ox();
        int y = Math.min(mc.getWindow().getGuiScaledHeight() / 2 + 26, Draw.hudTop(mc) - 40 - ph) + oy();
        int a = Math.max(0, Math.min(255, (int) (255 * anim)));

        bounds(x, y, x + pw, y + ph);
        Draw.panel(g, x, y, x + pw, y + ph, Draw.withAlpha(Draw.bg(), (Draw.bg() >>> 24) * a / 255));
        Draw.round(g, x + 2, y + 4, x + 4, y + ph - 4, 1, Draw.accent());

        int tx = x + 8;
        if (face) {
            PlayerFaceRenderer.draw(g, ((AbstractClientPlayer) t).getSkin(), x + 8, y + 5, 24);
            tx = x + 37;
        }
        String hpText = String.format(Locale.ROOT, "%.1f", hp);
        Draw.text(g, Draw.trim(t.getName().getString(), x + pw - 6 - Draw.width(hpText + " HP") - 4 - tx), tx, y + 5, Draw.WHITE);
        Draw.textRight(g, hpText + " HP", x + pw - 6, y + 5, absorption > 0 ? 0xFFFFD700 : Draw.healthColor(health, max));

        int bx1 = tx, bx2 = x + pw - 6, by = y + 17;
        Draw.round(g, bx1, by, bx2, by + 5, 2, 0xFF202028);
        float ft = Math.max(0f, Math.min(1f, trailHp / max));
        float f = Math.max(0f, Math.min(1f, shownHp / max));
        int full = bx2 - bx1;
        if (ft > f) Draw.rect(g, bx1 + (int) (full * f), by, bx1 + (int) (full * ft), by + 5, 0xC0FFFFFF);
        int fill = (int) (full * f);
        if (fill >= 4) Draw.round(g, bx1, by, bx1 + fill, by + 5, 2, Draw.healthColor(shownHp, max));
        else if (fill > 0) Draw.rect(g, bx1, by, bx1 + fill, by + 5, Draw.healthColor(shownHp, max));
        if (absorption > 0) {
            float fa = Math.max(0f, Math.min(1f, absorption / max));
            Draw.rect(g, bx1, by + 3, bx1 + (int) (full * fa), by + 5, 0xFFFFD700);
        }

        String info = (int) mc.player.distanceTo(t) + Lang.t(" м");
        String armor = HotbarView.armorSummary(t);
        if (armor != null) info += Lang.t("   броня ") + armor;
        Draw.text(g, info, tx, y + 26, Draw.GRAY);

        if (items.get()) {
            int ix = x + 8;
            int iy = y + ph - 21;
            for (EquipmentSlot s : SLOTS) {
                ItemStack st = t.getItemBySlot(s);
                Draw.round(g, ix - 1, iy - 1, ix + 17, iy + 17, 2, 0x40000000);
                if (!st.isEmpty()) Draw.item(g, st, ix, iy);
                ix += s == EquipmentSlot.OFFHAND ? 26 : 22;
            }
        }
    }

    @Override
    public void onWorldChange(Minecraft mc) {
        last = null;
    }
}

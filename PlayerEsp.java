package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;
import ru.visual.client.util.TargetManager;

public class PlayerEsp extends Module implements Config.DefaultOn {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final BoolSetting box = add(new BoolSetting("Рамка", "Рисует 2D-рамку вокруг игроков (видно сквозь стены).", true));
    private final BoolSetting healthBar = add(new BoolSetting("Полоса HP", "Вертикальная полоса здоровья слева от рамки.", true));
    private final BoolSetting nametag = add(new BoolSetting("Ник и HP", "Ник, здоровье и дистанция над игроком.", true));
    private final BoolSetting heldItem = add(new BoolSetting("Предмет в руке", "Показывает, что игрок держит в руке прямо сейчас (иконка и название).", true));
    private final BoolSetting armor = add(new BoolSetting("Броня", "Иконки надетой брони с прочностью под игроком.", true));
    private final BoolSetting mobs = add(new BoolSetting("Мобы", "Показывать и мобов (жёлтая рамка, без предметов).", false));
    private final ColorSetting color = add(new ColorSetting("Цвет рамки", "Цвет рамки игроков. Цель выделяется красным.", 0xFFFFFF));
    private final NumSetting range = add(new NumSetting("Дистанция", "Максимальная дистанция, на которой рисуются игроки (блоки).", 96, 16, 256, 8));

    public PlayerEsp() {
        super("PlayerESP", "Показывает игроков сквозь стены: рамка, ник, HP, предмет в руке и броня.", Category.VISUAL);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int count = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity p) || p == mc.player || p.isRemoved()) continue;
            boolean isPlayer = p instanceof Player;
            if (!isPlayer && (!mobs.get() || p.getType() == EntityType.ARMOR_STAND)) continue;
            if (mc.player.distanceTo(p) > range.get()) continue;
            if (++count > 50) break;

            Vec3 pos = p.getPosition(pt);
            double w = p.getBbWidth() / 2.0, h = p.getBbHeight();
            double[] r = Projection.boxRect(new AABB(pos.x - w, pos.y, pos.z - w, pos.x + w, pos.y + h, pos.z + w));
            if (r == null) continue;
            int x1 = (int) r[0], y1 = (int) r[1], x2 = (int) r[2], y2 = (int) r[3];
            int cx = (x1 + x2) / 2;
            boolean target = TargetManager.isTarget(p);
            int col = target ? 0xFFFF4040 : (isPlayer ? color.argb() : 0xFFFFD84A);
            float hp = p.getHealth() + p.getAbsorptionAmount();

            if (box.get()) {
                Draw.outline(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF000000);
                Draw.outline(g, x1, y1, x2, y2, col);
            }
            if (healthBar.get()) {
                int bx = x1 - 5;
                Draw.rect(g, bx - 1, y1 - 1, bx + 2, y2 + 1, 0xFF000000);
                float f = Math.max(0f, Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth())));
                int top = y2 - (int) ((y2 - y1) * f);
                Draw.rect(g, bx, top, bx + 1, y2, Draw.healthColor(p.getHealth(), p.getMaxHealth()));
            }
            if (nametag.get()) {
                String s = p.getName().getString() + " " + String.format(java.util.Locale.ROOT, "%.1f", hp)
                        + " " + (int) mc.player.distanceTo(p) + "m";
                int tw = Draw.width(s);
                Draw.rect(g, cx - tw / 2 - 2, y1 - 13, cx + tw / 2 + 2, y1 - 2, 0x90000000);
                Draw.text(g, s, cx - tw / 2, y1 - 12, col);
            }
            if (!isPlayer) continue;
            int below = y2 + 3;
            if (heldItem.get()) {
                below = drawHeld(g, p.getMainHandItem(), cx, below);
                below = drawHeld(g, p.getOffhandItem(), cx, below);
            }
            if (armor.get()) {
                int n = 0;
                for (EquipmentSlot s : ARMOR) if (!p.getItemBySlot(s).isEmpty()) n++;
                if (n > 0) {
                    int ax = cx - n * 8;
                    for (EquipmentSlot s : ARMOR) {
                        ItemStack st = p.getItemBySlot(s);
                        if (st.isEmpty()) continue;
                        g.renderItem(st, ax, below);
                        g.renderItemDecorations(mc.font, st, ax, below);
                        ax += 16;
                    }
                }
            }
        }
    }

    private int drawHeld(GuiGraphics g, ItemStack st, int cx, int y) {
        if (st.isEmpty()) return y;
        String name = st.getHoverName().getString() + (st.getCount() > 1 ? " x" + st.getCount() : "");
        int tw = Draw.width(name);
        int total = 18 + tw;
        int x = cx - total / 2;
        Draw.rect(g, x - 1, y - 1, x + total + 2, y + 17, 0x90000000);
        g.renderItem(st, x, y);
        Draw.text(g, name, x + 18, y + 4, 0xFFFFE070);
        return y + 19;
    }
}

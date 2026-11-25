package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;

public class ItemEsp extends Module {
    private final BoolSetting labels = add(new BoolSetting("Названия", "Подписывает предметы названием и количеством.", true));
    private final NumSetting range = add(new NumSetting("Дистанция", "Максимальная дистанция до предмета (блоки).", 48, 8, 128, 4));

    public ItemEsp() {
        super("ItemESP", "Подсвечивает выброшенные предметы на земле сквозь стены.", Category.VISUAL);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int count = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity ie) || ie.isRemoved()) continue;
            if (mc.player.distanceTo(ie) > range.get()) continue;
            if (++count > 60) break;
            Vec3 p = ie.getPosition(pt);
            double[] r = Projection.boxRect(new AABB(p.x - 0.25, p.y, p.z - 0.25, p.x + 0.25, p.y + 0.5, p.z + 0.25));
            if (r == null) continue;
            int x1 = (int) r[0], y1 = (int) r[1], x2 = (int) r[2], y2 = (int) r[3];
            Draw.outline(g, x1, y1, x2, y2, 0xFFFFD84A);
            if (labels.get()) {
                ItemStack st = ie.getItem();
                String s = st.getHoverName().getString() + (st.getCount() > 1 ? " x" + st.getCount() : "");
                Draw.textCentered(g, s, (x1 + x2) / 2, y1 - 10, 0xFFFFE9A0);
            }
        }
    }
}

package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;

public class ItemEsp extends Module {
    private final BoolSetting labels = add(new BoolSetting("Названия", "Подписывает предметы названием и количеством.", true));
    private final ModeSetting filter = add(new ModeSetting("Фильтр", "Все - любые предметы; Ценные - алмазы, незерит, элитры, тотемы, зачарованное, шалкеры и т.п.", "Все", "Все", "Ценные"));
    private final BoolSetting beam = add(new BoolSetting("Луч", "Вертикальный луч над предметом - видно издалека (как Item Highlight в FlyVisual).", false));
    private final NumSetting range = add(new NumSetting("Дистанция", "Максимальная дистанция до предмета (блоки).", 48, 8, 128, 4));

    public ItemEsp() {
        super("ItemESP", "Подсвечивает выброшенные предметы сквозь стены: ценные отдельным цветом, луч над предметом.", Category.VISUAL);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int count = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity ie) || ie.isRemoved()) continue;
            if (mc.player.distanceTo(ie) > range.get()) continue;
            boolean rare = valuable(ie.getItem());
            if (filter.is("Ценные") && !rare) continue;
            if (++count > 60) break;
            Vec3 p = ie.getPosition(pt);
            double[] r = Projection.boxRect(new AABB(p.x - 0.25, p.y, p.z - 0.25, p.x + 0.25, p.y + 0.5, p.z + 0.25));
            if (r == null) continue;
            int x1 = (int) r[0], y1 = (int) r[1], x2 = (int) r[2], y2 = (int) r[3];
            int col = rare ? 0xFF55FFFF : 0xFFFFD84A;
            Draw.outline(g, x1, y1, x2, y2, col);
            if (beam.get()) {
                double[] a = new double[3], b = new double[3];
                if (Projection.project(p.x, p.y + 0.25, p.z, a) && Projection.project(p.x, p.y + 6, p.z, b)) {
                    Draw.dottedLine(g, a[0], a[1], b[0], b[1], Draw.withAlpha(col, 0xB0), 1);
                }
            }
            if (labels.get()) {
                ItemStack st = ie.getItem();
                String s = st.getHoverName().getString() + (st.getCount() > 1 ? " x" + st.getCount() : "");
                Draw.textCentered(g, s, (x1 + x2) / 2, y1 - 10, rare ? 0xFF9AFFFF : 0xFFFFE9A0);
            }
        }
    }

    private static boolean valuable(ItemStack st) {
        if (st.isEnchanted() || st.is(Items.ENCHANTED_BOOK)) return true;
        if (st.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) return true;
        return st.is(Items.DIAMOND) || st.is(Items.DIAMOND_BLOCK) || st.is(Items.NETHERITE_INGOT) || st.is(Items.NETHERITE_SCRAP)
                || st.is(Items.ANCIENT_DEBRIS) || st.is(Items.NETHERITE_BLOCK) || st.is(Items.ELYTRA) || st.is(Items.TOTEM_OF_UNDYING)
                || st.is(Items.ENCHANTED_GOLDEN_APPLE) || st.is(Items.GOLDEN_APPLE) || st.is(Items.NETHER_STAR) || st.is(Items.BEACON)
                || st.is(Items.EMERALD_BLOCK) || st.is(Items.TRIDENT) || st.is(Items.MACE) || st.is(Items.HEAVY_CORE)
                || st.is(Items.DRAGON_EGG) || st.is(Items.SHULKER_SHELL) || st.is(Items.DIAMOND_ORE) || st.is(Items.DEEPSLATE_DIAMOND_ORE)
                || st.getItem().toString().contains("diamond_") || st.getItem().toString().contains("netherite_");
    }
}

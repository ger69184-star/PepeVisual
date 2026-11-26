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
    private final BoolSetting beam = add(new BoolSetting("Луч", "Вертикальный 3D-луч над каждым предметом (над ценными - всегда).", false));
    private final NumSetting range = add(new NumSetting("Дистанция", "Максимальная дистанция до предмета (блоки).", 48, 8, 128, 4));

    public ItemEsp() {
        super("ItemESP", "Подсвечивает выброшенные предметы сквозь стены: ценные отдельным цветом, луч над предметом.", Category.VISUAL);
    }

    @Override
    public void onWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, float pt, Minecraft mc) {
        int count = 0;
        double r2 = range.get() * range.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity ie) || ie.isRemoved() || ie.distanceToSqr(mc.player) > r2) continue;
            boolean rare = valuable(ie.getItem());
            if (filter.is("Ценные") && !rare) continue;
            if (++count > 60) break;
            Vec3 p = ie.getPosition(pt);
            int col = rare ? 0x55FFFF : 0xFFD84A;
            // мягкий круг на земле вокруг предмета
            ru.visual.client.util.R3D.circle(p.x, p.y + 0.02, p.z, 0.35, 20, Draw.withAlpha(col, 200), 1.5f);
            if (beam.get() || rare) {
                ru.visual.client.util.R3D.line(p.x, p.y + 0.3, p.z, p.x, p.y + (rare ? 8 : 4), p.z, Draw.withAlpha(col, rare ? 200 : 120), rare ? 2.5f : 1.5f);
            }
        }
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int count = 0;
        java.util.List<int[]> taken = new java.util.ArrayList<>();
        java.util.List<ItemEntity> list = new java.util.ArrayList<>();
        double r2 = range.get() * range.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity ie) || ie.isRemoved() || ie.distanceToSqr(mc.player) > r2) continue;
            if (filter.is("Ценные") && !valuable(ie.getItem())) continue;
            list.add(ie);
        }
        // ценные и ближние - первыми, их подписи важнее
        list.sort((a, b) -> {
            boolean ra = valuable(a.getItem()), rb = valuable(b.getItem());
            if (ra != rb) return ra ? -1 : 1;
            return Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player));
        });
        for (ItemEntity ie : list) {
            if (++count > 60) break;
            boolean rare = valuable(ie.getItem());
            Vec3 p = ie.getPosition(pt);
            double[] r = Projection.boxRect(new AABB(p.x - 0.2, p.y, p.z - 0.2, p.x + 0.2, p.y + 0.4, p.z + 0.2));
            if (r == null) continue;
            int x1 = (int) r[0], y1 = (int) r[1], x2 = (int) r[2], y2 = (int) r[3];
            int col = rare ? 0xFF55FFFF : 0xFFFFD84A;
            Draw.corners(g, x1, y1, x2, y2, col);
            if (labels.get() && taken.size() < 25) {
                ItemStack st = ie.getItem();
                String s = Draw.trim(st.getHoverName().getString(), 120) + (st.getCount() > 1 ? " x" + st.getCount() : "");
                int tw = Draw.width(s) + 6, cx = (x1 + x2) / 2;
                int[] want = {cx - tw / 2, y1 - 14, cx + tw / 2, y1 - 2};
                boolean free = true;
                for (int[] t : taken) if (want[0] < t[2] && want[2] > t[0] && want[1] < t[3] && want[3] > t[1]) { free = false; break; }
                if (free) taken.add(Draw.pill(g, s, cx, y1 - 12, rare ? 0xFF9AFFFF : 0xFFFFE9A0));
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

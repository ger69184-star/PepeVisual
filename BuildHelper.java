package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Blueprint;
import ru.visual.client.util.Blueprints;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Помощник строителя: призрачная проекция чертежа, список нужных блоков и подсказка, что ставить следующим.
 * Наведи на блок, выбери чертёж и включи "Зафиксировать место".
 */
public class BuildHelper extends Module {
    private record Cell(BlockPos pos, Block block, int layer, boolean wrong) {}

    private final ModeSetting blueprint = add(new ModeSetting("Чертёж", "Что строим. ЛКМ - следующий чертёж, ПКМ - предыдущий.", Blueprints.ALL.get(0).name, Blueprints.names()));
    private final ModeSetting rotate = add(new ModeSetting("Поворот", "Поворот чертежа на 90 градусов.", "0°", "0°", "90°", "180°", "270°"));
    private final BoolSetting lock = add(new BoolSetting("Зафиксировать место", "Включи, когда проекция стоит там, где нужно. Пока выключено - чертёж следует за твоим прицелом.", false));
    private final ModeSetting layerMode = add(new ModeSetting("Слои", "Авто - показывает самый нижний недостроенный слой. Можно показать все или один выбранный.", "Авто (снизу вверх)", "Авто (снизу вверх)", "Все слои", "Выбранный слой"));
    private final NumSetting layer = add(new NumSetting("Номер слоя", "Какой слой показывать в режиме 'Выбранный слой' (1 = нижний).", 1, 1, 8, 1));
    private final NumSetting shiftX = add(new NumSetting("Сдвиг X", "Точная подгонка положения чертежа по X (блоки).", 0, -10, 10, 1));
    private final NumSetting shiftY = add(new NumSetting("Сдвиг Y", "Точная подгонка положения чертежа по высоте (блоки).", 0, -10, 10, 1));
    private final NumSetting shiftZ = add(new NumSetting("Сдвиг Z", "Точная подгонка положения чертежа по Z (блоки).", 0, -10, 10, 1));
    private final NumSetting range = add(new NumSetting("Дальность показа", "Дальше этого расстояния клетки чертежа не рисуются (блоки).", 40, 8, 100, 4));
    private final NumSetting panelX = add(new NumSetting("Панель X", "Смещение информационной панели по горизонтали.", 0, -500, 500, 1));
    private final NumSetting panelY = add(new NumSetting("Панель Y", "Смещение информационной панели по вертикали.", 0, -300, 300, 1));

    private final List<Cell> cells = new ArrayList<>();
    private Cell next;
    private int total;
    private BlockPos base;
    private BlockPos anchor;
    private int timer;

    public BuildHelper() {
        super("BuildHelper", "Помощь строителям: проекция чертежа, сколько блоков нужно, какая комната и что ставить дальше.", Category.HELPERS);
    }

    @Override
    protected void onDisable() {
        cells.clear();
        next = null;
        anchor = null;
        base = null;
        lock.set(false);
    }

    private BlockPos preview(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK) {
            return h.getBlockPos().relative(h.getDirection());
        }
        return null;
    }

    private static boolean ok(Block expected, BlockState s) {
        if (s.is(expected)) return true;
        return expected == Blocks.DIRT && (s.is(Blocks.FARMLAND) || s.is(Blocks.GRASS_BLOCK));
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (lock.get()) {
            if (anchor == null) {
                anchor = preview(mc);
                if (anchor == null) lock.set(false);
            }
        } else {
            anchor = null;
        }
        BlockPos p = lock.get() && anchor != null ? anchor : preview(mc);
        if (p == null) {
            cells.clear();
            next = null;
            base = null;
            return;
        }
        base = p.offset(shiftX.asInt(), shiftY.asInt(), shiftZ.asInt());
        if (timer++ % 4 != 0 && !cells.isEmpty()) {
            pickNext(mc);
            return;
        }

        Blueprint bp = Blueprints.byName(blueprint.get());
        int k = rotate.index();
        cells.clear();
        total = 0;
        for (int y = 0; y < bp.sy; y++) {
            for (int z = 0; z < bp.sz; z++) {
                for (int x = 0; x < bp.sx; x++) {
                    Block b = bp.blockAt(x, y, z);
                    if (b == null) continue;
                    total++;
                    int dx, dz;
                    switch (k) {
                        case 1 -> { dx = (bp.sz - 1) - z; dz = x; }
                        case 2 -> { dx = (bp.sx - 1) - x; dz = (bp.sz - 1) - z; }
                        case 3 -> { dx = z; dz = (bp.sx - 1) - x; }
                        default -> { dx = x; dz = z; }
                    }
                    BlockPos wp = base.offset(dx, y, dz);
                    BlockState st = mc.level.getBlockState(wp);
                    if (!ok(b, st)) cells.add(new Cell(wp, b, y, !st.isAir()));
                }
            }
        }
        pickNext(mc);
    }

    private int lowestLayer() {
        int low = Integer.MAX_VALUE;
        for (Cell c : cells) low = Math.min(low, c.layer);
        return low;
    }

    private boolean visibleLayer(Cell c, int low) {
        return switch (layerMode.get()) {
            case "Все слои" -> true;
            case "Выбранный слой" -> c.layer == layer.asInt() - 1;
            default -> c.layer == low;
        };
    }

    private void pickNext(Minecraft mc) {
        next = null;
        int low = lowestLayer();
        double best = Double.MAX_VALUE;
        Vec3 me = mc.player.position();
        for (Cell c : cells) {
            if (!visibleLayer(c, low)) continue;
            double d = Vec3.atCenterOf(c.pos).distanceToSqr(me);
            if (d < best) {
                best = d;
                next = c;
            }
        }
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (base == null) {
            Draw.rect(g, 4, 180, 4 + 190, 180 + 24, Draw.bg());
            Draw.text(g, "BuildHelper: наведи прицел на блок,", 8, 184, Draw.WHITE);
            Draw.text(g, "куда поставить постройку", 8, 194, Draw.GRAY);
            return;
        }
        Vec3 me = mc.player.position();
        int low = lowestLayer();
        int drawn = 0;
        for (Cell c : cells) {
            if (!visibleLayer(c, low)) continue;
            if (Vec3.atCenterOf(c.pos).distanceToSqr(me) > range.get() * range.get()) continue;
            if (drawn++ > 300) break;
            double[] r = Projection.boxRect(new AABB(c.pos));
            if (r == null) continue;
            int col = c.wrong ? 0xFFFF4040 : 0xAA40D0FF;
            Draw.outline(g, (int) r[0], (int) r[1], (int) r[2], (int) r[3], col);
        }
        if (next != null) {
            double[] r = Projection.boxRect(new AABB(next.pos));
            if (r != null) {
                int x1 = (int) r[0], y1 = (int) r[1], x2 = (int) r[2], y2 = (int) r[3];
                Draw.outline(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFFFFE030);
                Draw.outline(g, x1, y1, x2, y2, 0xFFFFE030);
                Draw.textCentered(g, "Поставь: " + next.block.getName().getString(), (x1 + x2) / 2, y1 - 11, 0xFFFFE030);
            }
        }

        // Информационная панель
        Blueprint bp = Blueprints.byName(blueprint.get());
        Map<Block, Integer> need = new LinkedHashMap<>();
        for (Cell c : cells) need.merge(c.block, 1, Integer::sum);
        Map<Item, Integer> have = new HashMap<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty()) have.merge(st.getItem(), st.getCount(), Integer::sum);
        }

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add(bp.name + "  (" + rotate.get() + ")");
        colors.add(Draw.accent());
        lines.add("Осталось: " + cells.size() + " из " + total + " блоков");
        colors.add(cells.isEmpty() ? 0xFF7CFFB0 : Draw.WHITE);
        if (cells.isEmpty()) {
            lines.add("Готово! Постройка завершена.");
            colors.add(0xFF7CFFB0);
        }
        need.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(7).forEach(e -> {
            Item it = e.getKey().asItem();
            String name = e.getKey().getName().getString();
            if (it == Items.AIR) {
                lines.add(name + " x" + e.getValue() + "  (ведро)");
                colors.add(Draw.GRAY);
            } else {
                int h = have.getOrDefault(it, 0);
                lines.add(name + " x" + e.getValue() + "  есть " + h);
                colors.add(h >= e.getValue() ? 0xFF7CFFB0 : 0xFFFF6A6A);
            }
        });
        lines.add(lock.get() ? "Место зафиксировано: " + base.getX() + " " + base.getY() + " " + base.getZ()
                : "Проекция следует за прицелом. Включи 'Зафиксировать место'.");
        colors.add(Draw.GRAY);
        if (next != null) {
            lines.add("Далее: " + next.block.getName().getString() + " на " + next.pos.getX() + " " + next.pos.getY() + " " + next.pos.getZ());
            colors.add(0xFFFFE030);
        }

        int w = 0;
        for (String s : lines) w = Math.max(w, Draw.width(s));
        int x = 4 + panelX.asInt(), y = 180 + panelY.asInt();
        Draw.rect(g, x, y, x + w + 10, y + lines.size() * 11 + 6, Draw.bg());
        Draw.rect(g, x, y, x + 2, y + lines.size() * 11 + 6, Draw.accent());
        int yy = y + 4;
        for (int i = 0; i < lines.size(); i++) {
            Draw.text(g, lines.get(i), x + 6, yy, colors.get(i));
            yy += 11;
        }
    }
}

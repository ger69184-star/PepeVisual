package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Поиск приватов (баз игроков). Клиент не видит границы регионов, поэтому ищем скопления
 * сундуков, печек, кроватей, зельеварок и т.п. - там, где они стоят кучей, скорее всего чья-то база.
 */
public class BaseFinder extends Module {
    private record P(BlockPos pos, int weight) {}

    private static final class Cell {
        final List<P> pts = new ArrayList<>();
    }

    private record Cluster(Vec3 center, AABB box, int score) {}

    private final NumSetting minScore = add(new NumSetting("Минимум объектов", "Сколько хранилищ и станков рядом нужно, чтобы считать место приватом. Меньше - больше ложных срабатываний.", 6, 2, 30, 1));
    private final NumSetting range = add(new NumSetting("Радиус", "Радиус поиска в блоках (только в загруженных чанках).", 160, 48, 320, 16));
    private final BoolSetting enderChests = add(new BoolSetting("Считать эндер-сундуки", "Эндер-сундуки тоже считаются признаком базы.", false));
    private final BoolSetting list = add(new BoolSetting("Список ближайших", "Выводит сверху экрана координаты ближайших приватов.", true));
    private final BoolSetting notify = add(new BoolSetting("Уведомление в чат", "Пишет в чат, когда найден новый приват.", true));

    private List<Cluster> clusters = new ArrayList<>();
    private final Set<Long> notified = new HashSet<>();
    private ClientLevel lastLevel;
    private int timer;

    public BaseFinder() {
        super("BaseFinder", "Поиск приватов: находит скопления сундуков, печек, кроватей и зельеварок - базы игроков.", Category.HELPERS);
    }

    @Override
    protected void onEnable() {
        timer = 0;
    }

    @Override
    protected void onDisable() {
        clusters = new ArrayList<>();
        notified.clear();
    }

    private int weight(BlockEntity be) {
        if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity) return 1;
        if (be instanceof AbstractFurnaceBlockEntity || be instanceof BrewingStandBlockEntity) return 1;
        if (be instanceof BedBlockEntity || be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity) return 1;
        if (be instanceof EnchantingTableBlockEntity) return 2;
        if (be instanceof BeaconBlockEntity) return 3;
        if (be instanceof EnderChestBlockEntity) return enderChests.get() ? 1 : 0;
        return 0;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            clusters = new ArrayList<>();
            notified.clear();
            timer = 0;
        }
        if (timer++ % 40 != 0) return;
        scan(mc);
    }

    private void scan(Minecraft mc) {
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int cr = (int) Math.ceil(range.get() / 16.0);
        double maxSq = range.get() * range.get();
        Vec3 me = mc.player.position();

        Map<Long, Cell> cells = new HashMap<>();
        Map<Long, int[]> coords = new HashMap<>();
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                ChunkAccess ca = mc.level.getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (!(ca instanceof LevelChunk lc)) continue;
                for (BlockEntity be : lc.getBlockEntities().values()) {
                    int w = weight(be);
                    if (w <= 0) continue;
                    BlockPos pos = be.getBlockPos();
                    if (Vec3.atCenterOf(pos).distanceToSqr(me) > maxSq) continue;
                    int cx = pos.getX() >> 3, cy = pos.getY() >> 3, cz = pos.getZ() >> 3;
                    long key = BlockPos.asLong(cx, cy, cz);
                    cells.computeIfAbsent(key, k -> new Cell()).pts.add(new P(pos, w));
                    coords.putIfAbsent(key, new int[]{cx, cy, cz});
                }
            }
        }

        // Для каждой ячейки 8x8x8 суммируем её и 26 соседей
        record Cand(int score, List<P> pts) {}
        List<Cand> cands = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : coords.entrySet()) {
            int[] c = e.getValue();
            List<P> pts = new ArrayList<>();
            int score = 0;
            for (int ax = -1; ax <= 1; ax++) {
                for (int ay = -1; ay <= 1; ay++) {
                    for (int az = -1; az <= 1; az++) {
                        Cell cell = cells.get(BlockPos.asLong(c[0] + ax, c[1] + ay, c[2] + az));
                        if (cell == null) continue;
                        for (P p : cell.pts) {
                            pts.add(p);
                            score += p.weight;
                        }
                    }
                }
            }
            if (score >= minScore.get()) cands.add(new Cand(score, pts));
        }
        cands.sort(Comparator.comparingInt((Cand c) -> -c.score));

        List<Cluster> result = new ArrayList<>();
        for (Cand c : cands) {
            double sx = 0, sy = 0, sz = 0;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (P p : c.pts) {
                sx += p.pos.getX();
                sy += p.pos.getY();
                sz += p.pos.getZ();
                minX = Math.min(minX, p.pos.getX());
                minY = Math.min(minY, p.pos.getY());
                minZ = Math.min(minZ, p.pos.getZ());
                maxX = Math.max(maxX, p.pos.getX());
                maxY = Math.max(maxY, p.pos.getY());
                maxZ = Math.max(maxZ, p.pos.getZ());
            }
            Vec3 center = new Vec3(sx / c.pts.size() + 0.5, sy / c.pts.size() + 0.5, sz / c.pts.size() + 0.5);
            boolean close = false;
            for (Cluster o : result) {
                if (o.center.distanceToSqr(center) < 18 * 18) {
                    close = true;
                    break;
                }
            }
            if (close) continue;
            result.add(new Cluster(center, new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(0.5), c.score));
            if (notify.get()) {
                long key = BlockPos.asLong((int) center.x >> 4, 0, (int) center.z >> 4);
                if (notified.add(key)) {
                    mc.player.displayClientMessage(Component.literal(Lang.t("[PepeVisual] Возможный приват (") + c.score + Lang.t(" объектов): ")
                            + (int) center.x + " " + (int) center.y + " " + (int) center.z), false);
                }
            }
        }
        clusters = result;
    }

    @Override
    public void onWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, float pt, Minecraft mc) {
        for (Cluster c : clusters) {
            ru.visual.client.util.R3D.boxOutline(c.box, 0xC0FF7A30, 1.6f);
            // столб над центром - видно издалека, где именно база
            ru.visual.client.util.R3D.line(c.center.x, c.box.maxY, c.center.z, c.center.x, c.box.maxY + 40, c.center.z, 0x90FF7A30, 2.5f);
        }
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        int w = mc.getWindow().getGuiScaledWidth();
        Vec3 me = mc.player.position();
        double[] o = new double[3];
        for (Cluster c : clusters) {
            double dist = c.center.distanceTo(me);
            int col = 0xFFFF7A30;
            double[] r = Projection.boxRect(c.box);
            if (r != null) {
                Draw.corners(g, (int) r[0], (int) r[1], (int) r[2], (int) r[3], col);
            }
            if (Projection.project(c.center.x, c.center.y, c.center.z, o)) {
                String s = Lang.t("Приват? ") + c.score + Lang.t(" объектов, ") + (int) dist + Lang.t("м");
                Draw.pill(g, s, (int) o[0], (int) o[1] - 12, col);
            }
        }
        if (list.get() && !clusters.isEmpty()) {
            List<Cluster> near = new ArrayList<>(clusters);
            near.sort(Comparator.comparingDouble(c -> c.center.distanceToSqr(me)));
            int y = 66; // под списком DungeonFinder
            for (int i = 0; i < Math.min(3, near.size()); i++) {
                Cluster c = near.get(i);
                String s = Lang.t("Приват: ") + (int) c.center.x + " " + (int) c.center.y + " " + (int) c.center.z
                        + " (" + (int) c.center.distanceTo(me) + Lang.t("м, ") + c.score + ")";
                Draw.pill(g, s, w / 2, y + 2, 0xFFFF7A30);
                y += 14;
            }
        }
    }
}

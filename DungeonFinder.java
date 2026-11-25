package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DungeonFinder extends Module {
    private enum Kind {
        SPAWNER("Спавнер", 0xFFFF3FA0),
        TRIAL("Trial-спавнер", 0xFFFF7040),
        CHEST("Сундук", 0xFFFFD84A),
        ENDER("Эндер-сундук", 0xFFB070FF),
        BARREL("Бочка", 0xFFC08A50),
        SHULKER("Шалкер", 0xFF40E0E0);

        final String title;
        final int color;

        Kind(String title, int color) {
            this.title = title;
            this.color = color;
        }
    }

    private record Found(BlockPos pos, Kind kind, boolean dungeon) {}

    private final BoolSetting spawners = add(new BoolSetting("Спавнеры", "Искать спавнеры мобов (по ним находят данжи и шахты).", true));
    private final BoolSetting chests = add(new BoolSetting("Сундуки", "Искать обычные и ловушечные сундуки.", true));
    private final BoolSetting enderChests = add(new BoolSetting("Эндер-сундуки", "Искать эндер-сундуки.", false));
    private final BoolSetting barrels = add(new BoolSetting("Бочки", "Искать бочки.", false));
    private final BoolSetting shulkers = add(new BoolSetting("Шалкеры", "Искать шалкер-боксы.", false));
    private final BoolSetting list = add(new BoolSetting("Список ближайших", "Выводит сверху экрана координаты ближайших спавнеров.", true));
    private final BoolSetting lines = add(new BoolSetting("Линии к спавнерам", "Пунктирные линии от центра экрана к спавнерам.", true));
    private final BoolSetting notify = add(new BoolSetting("Уведомление в чат", "Пишет в чат, когда найден новый спавнер.", true));
    private final NumSetting range = add(new NumSetting("Радиус", "Радиус поиска в блоках (ищет только в уже загруженных чанках).", 128, 32, 256, 16));

    private List<Found> found = new ArrayList<>();
    private final Set<BlockPos> notified = new HashSet<>();
    private int timer;
    private ClientLevel lastLevel;

    public DungeonFinder() {
        super("DungeonFinder", "Ищет данжи: находит спавнеры и сундуки в загруженных чанках и подсвечивает их сквозь стены.", Category.HELPERS);
    }

    @Override
    protected void onEnable() {
        timer = 0;
    }

    @Override
    protected void onDisable() {
        found = new ArrayList<>();
        notified.clear();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            found = new ArrayList<>();
            notified.clear();
            timer = 0;
        }
        if (timer++ % 20 != 0) return;
        scan(mc);
    }

    private void scan(Minecraft mc) {
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        int cr = (int) Math.ceil(range.get() / 16.0);
        double maxSq = range.get() * range.get();
        Vec3 me = mc.player.position();
        List<Found> raw = new ArrayList<>();
        List<BlockPos> chestPositions = new ArrayList<>();

        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                ChunkAccess ca = mc.level.getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (!(ca instanceof LevelChunk lc)) continue;
                for (BlockEntity be : lc.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (Vec3.atCenterOf(pos).distanceToSqr(me) > maxSq) continue;
                    Kind k = null;
                    if (be instanceof SpawnerBlockEntity) { if (spawners.get()) k = Kind.SPAWNER; }
                    else if (be instanceof TrialSpawnerBlockEntity) { if (spawners.get()) k = Kind.TRIAL; }
                    else if (be instanceof ChestBlockEntity) { chestPositions.add(pos); if (chests.get()) k = Kind.CHEST; }
                    else if (be instanceof EnderChestBlockEntity) { if (enderChests.get()) k = Kind.ENDER; }
                    else if (be instanceof BarrelBlockEntity) { if (barrels.get()) k = Kind.BARREL; }
                    else if (be instanceof ShulkerBoxBlockEntity) { if (shulkers.get()) k = Kind.SHULKER; }
                    if (k != null) raw.add(new Found(pos, k, false));
                }
            }
        }

        // Данж = спавнер, рядом с которым есть сундук
        List<Found> result = new ArrayList<>(raw.size());
        for (Found f : raw) {
            boolean dungeon = false;
            if (f.kind == Kind.SPAWNER) {
                for (BlockPos c : chestPositions) {
                    if (c.distSqr(f.pos) <= 144) {
                        dungeon = true;
                        break;
                    }
                }
            }
            result.add(new Found(f.pos, f.kind, dungeon));
            if (notify.get() && (f.kind == Kind.SPAWNER || f.kind == Kind.TRIAL) && notified.add(f.pos)) {
                mc.player.displayClientMessage(Component.literal("[PepeVisual] " + (dungeon ? "Данж" : "Спавнер") + ": "
                        + f.pos.getX() + " " + f.pos.getY() + " " + f.pos.getZ()), false);
            }
        }
        found = result;
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        Vec3 me = mc.player.position();
        double[] o = new double[3];
        int labels = 0;

        for (Found f : found) {
            Vec3 c = Vec3.atCenterOf(f.pos);
            double dist = c.distanceTo(me);
            boolean spawner = f.kind == Kind.SPAWNER || f.kind == Kind.TRIAL;
            int color = f.dungeon ? 0xFFFF9020 : f.kind.color;

            if (lines.get() && spawner && Projection.project(c.x, c.y, c.z, o)) {
                Draw.dottedLine(g, w / 2.0, h / 2.0 + 10, o[0], o[1], Draw.withAlpha(color, 170), 5);
            }
            double[] r = Projection.boxRect(new AABB(f.pos));
            if (r == null) continue;
            int x1 = (int) r[0], y1 = (int) r[1], x2 = (int) r[2], y2 = (int) r[3];
            Draw.outline(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF000000);
            Draw.outline(g, x1, y1, x2, y2, color);
            if ((spawner || dist < 40) && labels++ < 40) {
                String name = f.dungeon ? "Данж (спавнер)" : f.kind.title;
                Draw.textCentered(g, name + " " + (int) dist + "м", (x1 + x2) / 2, y1 - 11, color);
            }
        }

        if (list.get()) {
            List<Found> near = new ArrayList<>();
            for (Found f : found) if (f.kind == Kind.SPAWNER || f.kind == Kind.TRIAL) near.add(f);
            near.sort(Comparator.comparingDouble(f -> Vec3.atCenterOf(f.pos).distanceToSqr(me)));
            int y = 4;
            for (int i = 0; i < Math.min(3, near.size()); i++) {
                Found f = near.get(i);
                String s = (f.dungeon ? "Данж: " : "Спавнер: ") + f.pos.getX() + " " + f.pos.getY() + " " + f.pos.getZ()
                        + " (" + (int) Vec3.atCenterOf(f.pos).distanceTo(me) + "м)";
                int tw = Draw.width(s);
                Draw.rect(g, w / 2 - tw / 2 - 3, y - 1, w / 2 + tw / 2 + 3, y + 10, 0x90000000);
                Draw.textCentered(g, s, w / 2, y, f.dungeon ? 0xFFFF9020 : 0xFFFF3FA0);
                y += 12;
            }
        }
    }
}

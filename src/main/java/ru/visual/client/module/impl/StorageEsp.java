package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;

import java.util.ArrayList;
import java.util.List;

/** Подсветка хранилищ сквозь стены: сундуки, бочки, шалкеры, эндер-сундуки, печки, воронки, спавнеры. */
public class StorageEsp extends Module {
    private record Found(BlockPos pos, int color, String name) {}

    private final BoolSetting chests = add(new BoolSetting("Сундуки", "Обычные сундуки и сундуки-ловушки.", true));
    private final BoolSetting ender = add(new BoolSetting("Эндер-сундуки", "Эндер-сундуки.", true));
    private final BoolSetting shulkers = add(new BoolSetting("Шалкеры", "Шалкеровые ящики.", true));
    private final BoolSetting barrels = add(new BoolSetting("Бочки", "Бочки.", true));
    private final BoolSetting furnaces = add(new BoolSetting("Печки", "Печи, плавильни, коптильни.", false));
    private final BoolSetting hoppers = add(new BoolSetting("Воронки и раздатчики", "Воронки, раздатчики, выбрасыватели.", false));
    private final BoolSetting spawners = add(new BoolSetting("Спавнеры", "Спавнеры мобов.", true));
    private final BoolSetting labels = add(new BoolSetting("Подписи", "Подписывать тип и расстояние.", false));
    private final NumSetting range = add(new NumSetting("Радиус (чанки)", "В скольких чанках вокруг искать.", 6, 1, 16, 1));

    private List<Found> found = new ArrayList<>();
    private int timer;

    public StorageEsp() {
        super("StorageESP", "Подсвечивает сундуки, шалкеры, бочки, эндер-сундуки и спавнеры сквозь стены.", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        found = new ArrayList<>();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null || timer++ % 10 != 0) return;
        List<Found> out = new ArrayList<>();
        int r = range.asInt();
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = mc.level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    Found f = classify(be);
                    if (f != null) out.add(f);
                }
            }
        }
        // раньше лимит 400 обрывал только один чанк (break из внутреннего цикла), и на базах список рос до тысяч.
        // Теперь оставляем 400 БЛИЖАЙШИХ.
        if (out.size() > 400) {
            BlockPos me = mc.player.blockPosition();
            out.sort(java.util.Comparator.comparingDouble(f -> f.pos().distSqr(me)));
            out = new ArrayList<>(out.subList(0, 400));
        }
        found = out;
    }

    private Found classify(BlockEntity be) {
        BlockPos p = be.getBlockPos();
        if (be instanceof TrappedChestBlockEntity) return chests.get() ? new Found(p, 0xFFFF6A3D, "Ловушка") : null;
        if (be instanceof ChestBlockEntity) return chests.get() ? new Found(p, 0xFFFFB13D, "Сундук") : null;
        if (be instanceof EnderChestBlockEntity) return ender.get() ? new Found(p, 0xFFB84DFF, "Эндер-сундук") : null;
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.get() ? new Found(p, 0xFFFF5FD2, "Шалкер") : null;
        if (be instanceof BarrelBlockEntity) return barrels.get() ? new Found(p, 0xFFC08A4A, "Бочка") : null;
        if (be instanceof AbstractFurnaceBlockEntity) return furnaces.get() ? new Found(p, 0xFFAAAAAA, "Печь") : null;
        if (be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity) return hoppers.get() ? new Found(p, 0xFF7A7A90, "Воронка") : null;
        if (be instanceof SpawnerBlockEntity) return spawners.get() ? new Found(p, 0xFF3DFF8A, "Спавнер") : null;
        return null;
    }

    @Override
    public String info() {
        return found.isEmpty() ? null : String.valueOf(found.size());
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        Vec3 eye = Projection.eye();
        int drawn = 0;
        for (Found f : found) {
            BlockPos p = f.pos();
            double[] r = Projection.boxRect(new AABB(p.getX() + 0.06, p.getY(), p.getZ() + 0.06, p.getX() + 0.94, p.getY() + 0.88, p.getZ() + 0.94));
            if (r == null) continue;
            if (++drawn > 250) break;
            int x1 = (int) r[0], y1 = (int) r[1], x2 = (int) r[2], y2 = (int) r[3];
            Draw.rect(g, x1, y1, x2, y2, Draw.withAlpha(f.color() & 0xFFFFFF, 0x30));
            Draw.outline(g, x1, y1, x2, y2, f.color());
            if (labels.get()) {
                int d = (int) eye.distanceTo(Vec3.atCenterOf(p));
                Draw.textCentered(g, f.name() + " " + d + "м", (x1 + x2) / 2, y1 - 10, f.color());
            }
        }
    }
}

package ru.visual.client.module.impl;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.R3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Подсветка мест, где могут появиться враждебные мобы (свет от блоков = 0).
 * Красный крестик - мобы спавнятся всегда, жёлтый - только ночью (днём там светит солнце).
 * Удобно, чтобы осветить базу или проверить ферму мобов.
 */
public class SpawnHighlight extends Module {
    private record Spot(BlockPos pos, boolean always) {}

    private final NumSetting radius = add(new NumSetting("Радиус", "В каком радиусе вокруг тебя искать (блоки).", 16, 4, 32, 1));
    private final NumSetting height = add(new NumSetting("Высота", "Сколько блоков вверх и вниз проверять.", 8, 2, 24, 1));
    private final BoolSetting night = add(new BoolSetting("Показывать ночные", "Жёлтым - места, где мобы появятся только ночью (под открытым небом).", true));

    private volatile List<Spot> spots = new ArrayList<>();
    private int timer;

    public SpawnHighlight() {
        super("SpawnHighlight", "Показывает блоки, где могут появиться враждебные мобы: красный - всегда, жёлтый - ночью. Помогает осветить базу.", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        spots = new ArrayList<>();
    }

    @Override
    public String info() {
        return spots.isEmpty() ? null : String.valueOf(spots.size());
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null || timer++ % 10 != 0) return;
        ClientLevel level = mc.level;
        BlockPos c = mc.player.blockPosition();
        int r = radius.asInt(), h = height.asInt();
        boolean skyLit = level.dimensionType().hasSkyLight();
        int blockLimit = level.dimensionType().monsterSettings().monsterSpawnBlockLightLimit(); // 0 в обычном мире, 15 в Незере
        List<Spot> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        scan:
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int dy = -h; dy <= h; dy++) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!level.hasChunkAt(p)) continue;
                    if (!spawnable(level, p, below)) continue;
                    if (level.getBrightness(LightLayer.BLOCK, p) > blockLimit) continue;
                    boolean always = !skyLit || level.getBrightness(LightLayer.SKY, p) == 0;
                    if (!always && !night.get()) continue;
                    out.add(new Spot(p.immutable(), always));
                    if (out.size() > 3000) break scan; // раньше break выходил только из одного столбца
                }
            }
        }
        spots = out;
    }

    private static boolean spawnable(ClientLevel level, BlockPos.MutableBlockPos p, BlockPos.MutableBlockPos below) {
        BlockState at = level.getBlockState(p);
        if (!empty(level, p, at)) return false;
        below.set(p.getX(), p.getY() - 1, p.getZ());
        BlockState floor = level.getBlockState(below);
        if (!floor.isValidSpawn(level, below, EntityType.ZOMBIE)) return false;
        BlockPos head = p.above();
        return empty(level, head, level.getBlockState(head));
    }

    private static boolean empty(ClientLevel level, BlockPos pos, BlockState st) {
        return st.getCollisionShape(level, pos).isEmpty() && !st.isSignalSource() && st.getFluidState().isEmpty()
                && !st.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE);
    }

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        for (Spot s : spots) {
            double x = s.pos.getX(), y = s.pos.getY() + 0.02, z = s.pos.getZ();
            int col = s.always ? 0xD0FF3030 : 0xB0FFD030;
            R3D.line(x + 0.2, y, z + 0.2, x + 0.8, y, z + 0.8, col, 1.6f);
            R3D.line(x + 0.8, y, z + 0.2, x + 0.2, y, z + 0.8, col, 1.6f);
        }
    }
}

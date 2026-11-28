package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.VisualClient;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Blueprint;
import ru.visual.client.util.Blueprints;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;
import ru.visual.client.util.R3D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Режим строительства по чертежам-гайдам.
 * <ol>
 *     <li>Выбери чертёж в меню и включи функцию.</li>
 *     <li>[G] - первый угол зоны, [G] - второй угол (зона подсвечивается, видно, влезает ли постройка).</li>
 *     <li>Проверь проекцию, список материалов и препятствия, [R] - повернуть, [G] - начать.</li>
 *     <li>Стройка по шагам: подсвечивается блок, который ставить сейчас, стрелка показывает, куда смотреть,
 *     на панели - как именно ставить. Поставил правильно - шаг засчитан, подсвечивается следующий.</li>
 * </ol>
 */
public class BuildHelper extends Module {
    private enum Stage { SELECT_A, SELECT_B, CONFIRM, BUILD, DONE }

    private enum St { OK, MISSING, WRONG }

    private static final class Cell {
        final BlockPos pos;
        final Blueprint.Spec spec;
        final BlockState want;
        final int layer;
        final int phase;
        St st = St.MISSING;

        Cell(BlockPos pos, Blueprint.Spec spec, BlockState want, int layer, int phase) {
            this.pos = pos;
            this.spec = spec;
            this.want = want;
            this.layer = layer;
            this.phase = phase;
        }
    }

    private final ModeSetting blueprint = add(new ModeSetting("Чертёж", "Что строим. ЛКМ - следующий чертёж, ПКМ - предыдущий.", Blueprints.ALL.get(0).name, Blueprints.names()));
    private final ModeSetting rotate = add(new ModeSetting("Поворот", "Поворот чертежа (клавиша R во время выбора).", "0°", "0°", "90°", "180°", "270°"));
    private final ModeSetting view = add(new ModeSetting("Показ", "Пошагово - текущий блок и несколько следующих. Слой - весь текущий слой. Всё - все недостающие блоки.",
            "Пошагово", "Пошагово", "Слой", "Всё"));
    private final NumSetting ahead = add(new NumSetting("Шагов вперёд", "Сколько следующих шагов подсвечивать (пошаговый режим).", 3, 0, 12, 1));
    private final NumSetting shiftX = add(new NumSetting("Сдвиг X", "Подгонка положения по X (блоки).", 0, -10, 10, 1));
    private final NumSetting shiftY = add(new NumSetting("Сдвиг Y", "Подгонка положения по высоте (блоки).", 0, -30, 30, 1));
    private final NumSetting shiftZ = add(new NumSetting("Сдвиг Z", "Подгонка положения по Z (блоки).", 0, -10, 10, 1));
    private final NumSetting range = add(new NumSetting("Дальность показа", "Дальше этого расстояния клетки не рисуются (блоки).", 48, 8, 128, 4));
    private final BoolSetting sound = add(new BoolSetting("Звук шага", "Короткий звук, когда шаг выполнен.", true));
    private final BoolSetting autoSlot = add(new BoolSetting("Авто-выбор блока", "Когда шаг сменился, сам берёт в руку нужный блок из хотбара.", true));
    private final BoolSetting walls = add(new BoolSetting("Метка сквозь стены", "Подпись текущего шага видна сквозь блоки.", true));
    private final NumSetting panelX = add(new NumSetting("Панель X", "Смещение информационной панели по горизонтали.", 0, -500, 500, 1));
    private final NumSetting panelY = add(new NumSetting("Панель Y", "Смещение информационной панели по вертикали.", 0, -300, 300, 1));

    private Stage stage = Stage.SELECT_A;
    private BlockPos cornerA, cornerB;
    private BlockPos origin;
    private int rot;
    private String bpName;
    private BlockPos alignedTo;
    /** Стык 4 чанков, по которому выровнена ферма (x, z - линия сетки), или null. */
    private BlockPos chunkAligned;
    private final List<Cell> cells = new ArrayList<>();
    private final Set<BlockPos> skipped = new HashSet<>();
    private final Map<BlockPos, Long> recentDone = new HashMap<>();
    private Cell current;
    private BlockPos autoSlotFor;
    private final List<Cell> upcoming = new ArrayList<>();
    private int total, done, wrong, existingMissing;
    private int timer;
    private String message = "";
    private long messageTime;

    public BuildHelper() {
        super("BuildHelper", "Строительство по чертежам-гайдам: фермы, мобофермы, зельеварни. Выдели зону, получи список материалов и пошаговую подсветку, куда что ставить.", Category.HELPERS);
    }

    @Override
    public String info() {
        return switch (stage) {
            case BUILD -> done + "/" + total;
            case DONE -> Lang.t("готово");
            default -> null;
        };
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        stage = Stage.SELECT_A;
        cornerA = cornerB = origin = null;
        alignedTo = null;
        chunkAligned = null;
        cells.clear();
        skipped.clear();
        recentDone.clear();
        upcoming.clear();
        current = null;
        autoSlotFor = null;
    }

    private Blueprint bp() {
        return Blueprints.byName(blueprint.get());
    }

    private void say(String s) {
        message = s;
        messageTime = System.currentTimeMillis();
    }

    private BlockPos lookBlock(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK) return h.getBlockPos();
        return null;
    }

    // ---------- геометрия ----------

    private int width(Blueprint bp, int r) {
        return r % 2 == 0 ? bp.sx : bp.sz;
    }

    private int depth(Blueprint bp, int r) {
        return r % 2 == 0 ? bp.sz : bp.sx;
    }

    /** Локальные x,z чертежа -> смещение в мире при повороте r (по часовой стрелке). */
    private static int[] local(Blueprint bp, int x, int z, int r) {
        return switch (r) {
            case 1 -> new int[]{(bp.sz - 1) - z, x};
            case 2 -> new int[]{(bp.sx - 1) - x, (bp.sz - 1) - z};
            case 3 -> new int[]{z, (bp.sx - 1) - x};
            default -> new int[]{x, z};
        };
    }

    private BlockPos originForZone(Blueprint bp, int r, BlockPos a, BlockPos b) {
        int w = width(bp, r), d = depth(bp, r);
        int minX = Math.min(a.getX(), b.getX()), minZ = Math.min(a.getZ(), b.getZ());
        int zx = Math.abs(a.getX() - b.getX()) + 1, zz = Math.abs(a.getZ() - b.getZ()) + 1;
        int ox = minX + Math.max(0, (zx - w) / 2) + shiftX.asInt();
        int oz = minZ + Math.max(0, (zz - d) / 2) + shiftZ.asInt();
        int oy = Math.min(a.getY(), b.getY()) + 1 - bp.ground + shiftY.asInt();
        return new BlockPos(ox, oy, oz);
    }

    /** Точка стыка чанков чертежа (линия сетки) -> смещение от origin при повороте r. */
    private static int[] cornerLocal(Blueprint bp, int r) {
        int cx = bp.chunkCorner[0], cz = bp.chunkCorner[1];
        return switch (r) {
            case 1 -> new int[]{bp.sz - cz, cx};
            case 2 -> new int[]{bp.sx - cx, bp.sz - cz};
            case 3 -> new int[]{cz, bp.sx - cx};
            default -> new int[]{cx, cz};
        };
    }

    private boolean fits(Blueprint bp, int r, BlockPos a, BlockPos b) {
        int zx = Math.abs(a.getX() - b.getX()) + 1, zz = Math.abs(a.getZ() - b.getZ()) + 1;
        return zx >= width(bp, r) && zz >= depth(bp, r);
    }

    /** Чертёж с "уже существующим" блоком (спавнер): ищем такой блок рядом и выравниваем по нему. */
    private BlockPos alignToExisting(Minecraft mc, Blueprint bp, int r, BlockPos center) {
        alignedTo = null;
        int ex = -1, ey = -1, ez = -1;
        Block want = null;
        outer:
        for (int y = 0; y < bp.sy; y++) for (int z = 0; z < bp.sz; z++) for (int x = 0; x < bp.sx; x++) {
            Blueprint.Spec s = bp.specAt(x, y, z);
            if (s != null && s.match() == Blueprint.Match.EXISTING) {
                ex = x; ey = y; ez = z;
                want = s.block();
                break outer;
            }
        }
        if (want == null) return null;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int cx = center.getX() >> 4, cz = center.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            ChunkAccess ca = mc.level.getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
            if (!(ca instanceof LevelChunk lc)) continue;
            for (BlockEntity be : lc.getBlockEntities().values()) {
                if (!be.getBlockState().is(want)) continue;
                double d = be.getBlockPos().distSqr(center);
                if (d < bestD && d < 24 * 24) {
                    bestD = d;
                    best = be.getBlockPos();
                }
            }
        }
        if (best == null) return null;
        alignedTo = best;
        int[] l = local(bp, ex, ez, r);
        return best.offset(-l[0], -ey, -l[1]);
    }

    private void buildCells(Minecraft mc) {
        Blueprint bp = bp();
        cells.clear();
        skipped.clear();
        recentDone.clear();
        bpName = bp.name;
        if (origin == null) return;
        for (int y = 0; y < bp.sy; y++) {
            for (int z = 0; z < bp.sz; z++) {
                for (int x = 0; x < bp.sx; x++) {
                    Blueprint.Spec s = bp.specAt(x, y, z);
                    if (s == null) continue;
                    // змейка: чётные ряды слева направо, нечётные - справа налево (меньше беготни)
                    int[] l = local(bp, x, z, rot);
                    BlockState want = Blueprint.rotate(s.state(), rot);
                    cells.add(new Cell(origin.offset(l[0], y, l[1]), s, want, y, Blueprint.phase(s, want)));
                }
            }
        }
        // порядок: сначала выкопать (сверху вниз), затем слои снизу вверх, внутри слоя - по фазам и змейкой
        cells.sort(Comparator.<Cell>comparingInt(c -> c.phase < 0 ? 0 : 1)
                .thenComparingInt(c -> c.phase < 0 ? -c.layer : c.layer)
                .thenComparingInt(c -> c.phase)
                .thenComparingInt(c -> snake(bp, c)));
        timer = 0;
        scan(mc, false);
    }

    private int snake(Blueprint bp, Cell c) {
        int lz = c.pos.getZ() - origin.getZ(), lx = c.pos.getX() - origin.getX();
        int w = width(bp, rot);
        return lz * 1000 + ((lz & 1) == 0 ? lx : w - lx);
    }

    // ---------- клавиши ----------

    private static boolean pressed(KeyMapping k) {
        boolean any = false;
        while (k != null && k.consumeClick()) any = true;
        return any;
    }

    private void handleKeys(Minecraft mc) {
        if (mc.screen != null) return;
        Blueprint bp = bp();
        if (pressed(VisualClient.buildRotate)) {
            rotate.cycle(1);
            ru.visual.client.module.ModuleManager.markDirty();
            if (stage == Stage.CONFIRM || stage == Stage.BUILD) recomputeFromZone(mc);
            say(Lang.t("Поворот: ") + rotate.get());
        }
        if (pressed(VisualClient.buildCancel)) {
            switch (stage) {
                case SELECT_B, CONFIRM, DONE -> { reset(); say(Lang.t("Выбор зоны сброшен")); }
                case BUILD -> { stage = Stage.CONFIRM; say(Lang.t("Пауза. G - продолжить")); }
                default -> {}
            }
        }
        if (pressed(VisualClient.buildConfirm)) {
            BlockPos look = lookBlock(mc);
            switch (stage) {
                case SELECT_A -> {
                    if (look == null) { say(Lang.t("Наведи прицел на блок - первый угол зоны")); return; }
                    cornerA = look;
                    stage = Stage.SELECT_B;
                    say(Lang.t("Первый угол: ") + look.toShortString());
                }
                case SELECT_B -> {
                    cornerB = look != null ? look : cornerA;
                    if (!fits(bp, rot, cornerA, cornerB) && fits(bp, (rot + 1) % 4, cornerA, cornerB)) {
                        rotate.cycle(1);
                        say(Lang.t("Повернул чертёж, чтобы влез в зону"));
                    }
                    recomputeFromZone(mc);
                    stage = Stage.CONFIRM;
                }
                case CONFIRM -> {
                    stage = Stage.BUILD;
                    say(Lang.t("Начинаем! Ставь подсвеченный блок"));
                }
                case BUILD -> {
                    if (current != null) {
                        skipped.add(current.pos);
                        say(Lang.t("Шаг пропущен"));
                        scan(mc, false);
                    }
                }
                case DONE -> reset();
            }
        }
    }

    private void recomputeFromZone(Minecraft mc) {
        Blueprint bp = bp();
        rot = rotate.index();
        if (cornerA == null) return;
        BlockPos b = cornerB != null ? cornerB : cornerA;
        origin = originForZone(bp, rot, cornerA, b);
        BlockPos center = new BlockPos((cornerA.getX() + b.getX()) / 2, (cornerA.getY() + b.getY()) / 2, (cornerA.getZ() + b.getZ()) / 2);
        BlockPos al = alignToExisting(mc, bp, rot, center);
        chunkAligned = null;
        if (al != null) {
            origin = al.offset(shiftX.asInt(), shiftY.asInt(), shiftZ.asInt());
            say(Lang.t("Чертёж выровнен по спавнеру"));
        } else if (bp.chunkCorner != null) {
            // фермы FunTime: лимит мобов - на чанк, поэтому центр фермы ставим ровно на стык 4 чанков
            int[] c = cornerLocal(bp, rot);
            int wx = Math.floorDiv(center.getX() + 8, 16) * 16, wz = Math.floorDiv(center.getZ() + 8, 16) * 16;
            origin = new BlockPos(wx - c[0] + shiftX.asInt(), origin.getY(), wz - c[1] + shiftZ.asInt());
            chunkAligned = new BlockPos(wx, origin.getY(), wz);
            say(Lang.t("Ферма выровнена по стыку 4 чанков"));
        }
        buildCells(mc);
    }

    // ---------- тик ----------

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        handleKeys(mc);
        Blueprint bp = bp();
        // сменили чертёж или поворот в меню
        if ((stage == Stage.CONFIRM || stage == Stage.BUILD || stage == Stage.DONE)
                && (!bp.name.equals(bpName) || rot != rotate.index())) {
            recomputeFromZone(mc);
            if (stage == Stage.DONE) stage = Stage.CONFIRM;
        }
        if (stage == Stage.SELECT_A || stage == Stage.SELECT_B) {
            rot = rotate.index();
            return;
        }
        if (timer++ % 3 == 0) scan(mc, true);
        if (stage == Stage.BUILD && autoSlot.get() && mc.screen == null) pickSlot(mc);
    }

    /** Берёт в руку предмет для текущего шага (один раз на шаг, чтобы не мешать игроку). */
    private void pickSlot(Minecraft mc) {
        if (current == null || current.st != St.MISSING) return;
        if (current.pos.equals(autoSlotFor)) return;
        autoSlotFor = current.pos;
        var inv = mc.player.getInventory();
        if (fits(current.spec, inv.getItem(inv.getSelectedSlot()))) return;
        for (int i = 0; i < 9; i++) {
            if (fits(current.spec, inv.getItem(i))) {
                inv.setSelectedSlot(i);
                return;
            }
        }
    }

    private static boolean sameItem(Blueprint.Spec spec, Item it) {
        if (it == spec.item()) return true;
        Block b = spec.block();
        if (b == net.minecraft.world.level.block.Blocks.FARMLAND && (it == Items.DIRT || it == Items.GRASS_BLOCK)) return true;
        for (Block alt : spec.alts()) if (alt.asItem() == it) return true;
        return false;
    }

    private static boolean fits(Blueprint.Spec spec, ItemStack st) {
        if (st.isEmpty()) return false;
        return switch (spec.match()) {
            case AIR, EXISTING -> false;
            case WATER -> st.is(Items.WATER_BUCKET);
            case LAVA -> st.is(Items.LAVA_BUCKET);
            case SOLID -> st.getItem() instanceof BlockItem bi
                    && bi.getBlock().defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    && !bi.getBlock().defaultBlockState().hasBlockEntity();
            default -> sameItem(spec, st.getItem());
        };
    }

    private void scan(Minecraft mc, boolean notify) {
        if (mc.level == null) return;
        total = 0;
        done = 0;
        wrong = 0;
        existingMissing = 0;
        Cell prevCurrent = current;
        current = null;
        upcoming.clear();
        int want = ahead.asInt();
        long now = System.currentTimeMillis();
        for (Cell c : cells) {
            BlockState have = mc.level.getBlockState(c.pos);
            St before = c.st;
            boolean ok = c.spec.matches(mc.level, c.pos, have, c.want);
            if (c.spec.match() == Blueprint.Match.EXISTING) {
                c.st = ok ? St.OK : St.MISSING;
                if (!ok) existingMissing++;
                continue;
            }
            if (ok) c.st = St.OK;
            else if (c.spec.match() == Blueprint.Match.AIR) c.st = St.WRONG;
            else if (have.isAir() || have.canBeReplaced()) c.st = St.MISSING;
            else c.st = St.WRONG;
            if (c.spec.match() != Blueprint.Match.AIR) total++;
            if (c.st == St.OK) {
                if (c.spec.match() != Blueprint.Match.AIR) done++;
                if (before != St.OK && notify) recentDone.put(c.pos, now);
                continue;
            }
            if (c.st == St.WRONG) wrong++;
            if (skipped.contains(c.pos)) continue;
            if (current == null) current = c;
            else if (upcoming.size() < want) upcoming.add(c);
        }
        if (notify && stage == Stage.BUILD && prevCurrent != null && prevCurrent.st == St.OK && sound.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.8f));
        }
        if (stage == Stage.BUILD && current == null && !cells.isEmpty()) {
            stage = Stage.DONE;
            if (sound.get()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f));
            mc.player.displayClientMessage(Component.literal(Lang.t("[PepeVisual] Постройка \"") + Lang.t(bp().name) + Lang.t("\" готова!")), false);
        }
        recentDone.values().removeIf(t -> now - t > 900);
    }

    // ---------- материалы ----------

    private Map<String, int[]> materials(Minecraft mc) {
        Map<String, int[]> need = new LinkedHashMap<>();
        Map<Item, Integer> have = new HashMap<>();
        int solidHave = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            have.merge(st.getItem(), st.getCount(), Integer::sum);
            if (st.getItem() instanceof BlockItem bi
                    && bi.getBlock().defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    && !bi.getBlock().defaultBlockState().hasBlockEntity()) solidHave += st.getCount();
        }
        for (Cell c : cells) {
            if (c.st == St.OK || c.spec.match() == Blueprint.Match.AIR || c.spec.match() == Blueprint.Match.EXISTING) continue;
            String key = c.spec.displayName();
            int[] v = need.computeIfAbsent(key, k -> new int[]{0, 0});
            v[0]++;
            if (c.spec.match() == Blueprint.Match.SOLID) v[1] = solidHave;
            else {
                Item it = c.spec.item();
                if (c.spec.match() != Blueprint.Match.WATER && c.spec.match() != Blueprint.Match.LAVA) {
                    int h = have.getOrDefault(it, 0);
                    for (Block alt : c.spec.alts()) if (alt.asItem() != it) h += have.getOrDefault(alt.asItem(), 0);
                    if (c.spec.block() == net.minecraft.world.level.block.Blocks.FARMLAND) h += have.getOrDefault(Items.DIRT, 0) + have.getOrDefault(Items.GRASS_BLOCK, 0);
                    v[1] = h;
                } else {
                    v[1] = have.getOrDefault(it, 0) > 0 ? 999 : 0;
                }
            }
        }
        return need;
    }

    // ---------- отрисовка в мире ----------

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        Blueprint bp = bp();
        long now = System.currentTimeMillis();
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 180.0));

        if (stage == Stage.SELECT_A) {
            BlockPos look = lookBlock(mc);
            if (look == null) return;
            R3D.boxFilled(new AABB(look).inflate(0.01), Draw.withAlpha(0xFFE030, (int) (50 + 40 * pulse)));
            R3D.blockOutline(look, 0xFFFFE030, 2.5f);
            int w = width(bp, rot), d = depth(bp, rot);
            BlockPos o = new BlockPos(look.getX() - w / 2, look.getY() + 1 - bp.ground, look.getZ() - d / 2);
            R3D.boxOutline(new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + w, o.getY() + bp.sy, o.getZ() + d), 0x90FFFFFF, 1.5f);
            return;
        }
        if (stage == Stage.SELECT_B) {
            BlockPos look = lookBlock(mc);
            BlockPos b = look != null ? look : cornerA;
            boolean fit = fits(bp, rot, cornerA, b) || fits(bp, (rot + 1) % 4, cornerA, b);
            int col = fit ? 0x40FF90 : 0xFF5050;
            AABB zone = new AABB(Math.min(cornerA.getX(), b.getX()), Math.min(cornerA.getY(), b.getY()), Math.min(cornerA.getZ(), b.getZ()),
                    Math.max(cornerA.getX(), b.getX()) + 1, Math.max(cornerA.getY(), b.getY()) + 1, Math.max(cornerA.getZ(), b.getZ()) + 1);
            R3D.boxFilled(zone.inflate(0.01), Draw.withAlpha(col, 45));
            R3D.boxOutline(zone.inflate(0.01), 0xFF000000 | col, 2.5f);
            R3D.blockOutline(cornerA, 0xFFFFE030, 3f);
            if (look != null) R3D.blockOutline(look, 0xFFFFE030, 3f);
            return;
        }
        if (origin == null) return;
        int w = width(bp, rot), d = depth(bp, rot);
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + w, origin.getY() + bp.sy, origin.getZ() + d);
        R3D.boxOutline(bounds.inflate(0.02), stage == Stage.DONE ? 0xC040FF90 : 0x80FFFFFF, 1.5f);
        if (chunkAligned != null) {
            // стык чанков: вертикальная линия и крест по границам чанков
            double cx = chunkAligned.getX(), cz = chunkAligned.getZ(), y0 = origin.getY(), y1 = origin.getY() + bp.sy + 2;
            R3D.line(cx, y0, cz, cx, y1, cz, 0xFFFF60D0, 3f);
            double yy = origin.getY() + bp.ground + 0.02;
            R3D.line(cx - w, yy, cz, cx + w, yy, cz, 0xA0FF60D0, 1.5f);
            R3D.line(cx, yy, cz - d, cx, yy, cz + d, 0xA0FF60D0, 1.5f);
        }
        if (stage == Stage.DONE) return;

        Vec3 me = mc.player.position();
        double r2 = range.get() * range.get();
        int layer = current != null ? current.layer : -1;
        boolean all = stage == Stage.CONFIRM || view.is("Всё");
        int drawn = 0;
        for (Cell c : cells) {
            if (c.st == St.OK || c == current) continue;
            if (Vec3.atCenterOf(c.pos).distanceToSqr(me) > r2) continue;
            if (drawn > 1500) break;
            if (c.spec.match() == Blueprint.Match.EXISTING) {
                R3D.blockOutline(c.pos, 0xFFFFA030, 2f);
                drawn++;
                continue;
            }
            if (c.st == St.WRONG) {
                AABB box = new AABB(c.pos).deflate(0.03);
                R3D.boxFilled(box, Draw.withAlpha(0xFF3030, 45));
                R3D.boxOutline(box, 0xD0FF4040, 1.5f);
                drawn++;
                continue;
            }
            boolean show = all || (view.is("Слой") && c.layer == layer) || (view.is("Пошагово") && c.layer == layer);
            if (!show) continue;
            int alpha = view.is("Пошагово") && stage == Stage.BUILD ? 45 : 110;
            R3D.boxOutline(new AABB(c.pos).deflate(0.06), Draw.withAlpha(0x40D0FF, alpha), 1.2f);
            drawn++;
        }
        if (stage == Stage.BUILD) {
            for (int i = 0; i < upcoming.size(); i++) {
                Cell c = upcoming.get(i);
                int a = 200 - i * (150 / Math.max(1, upcoming.size()));
                R3D.boxOutline(new AABB(c.pos).deflate(0.04), Draw.withAlpha(0x40D0FF, a), 2f);
            }
            if (current != null) {
                AABB box = new AABB(current.pos).inflate(0.005);
                int col = current.st == St.WRONG ? 0xFF4040 : 0xFFE030;
                R3D.boxFilled(box, Draw.withAlpha(col, (int) (50 + 70 * pulse)));
                R3D.boxOutline(box, 0xFF000000 | col, 3f);
                Direction look = Blueprint.lookDirection(current.want);
                if (look != null && current.st != St.WRONG) R3D.arrow(current.pos, look, 0xFFFFFFFF, 3f);
                // вертикальный "маяк", чтобы найти блок издалека
                Vec3 c = Vec3.atCenterOf(current.pos);
                R3D.line(c.x, c.y + 0.5, c.z, c.x, c.y + 6, c.z, Draw.withAlpha(col, 160), 2f);
            }
        }
        for (Map.Entry<BlockPos, Long> e : recentDone.entrySet()) {
            float f = 1f - (now - e.getValue()) / 900f;
            if (f <= 0) continue;
            AABB box = new AABB(e.getKey()).inflate(0.01 + 0.08 * (1 - f));
            R3D.boxFilled(box, Draw.withAlpha(0x40FF90, (int) (110 * f)));
            R3D.boxOutline(box, Draw.withAlpha(0x40FF90, (int) (255 * f)), 2f);
        }
    }

    // ---------- HUD ----------

    private static String key(KeyMapping k) {
        return k == null ? "?" : k.getTranslatedKeyMessage().getString();
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        Blueprint bp = bp();
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        String kc = key(VisualClient.buildConfirm), kr = key(VisualClient.buildRotate), kx = key(VisualClient.buildCancel);
        int ok = 0xFF7CFFB0, bad = 0xFFFF6A6A, yellow = 0xFFFFE030;

        add(lines, colors, Lang.t(bp.name) + "  [" + Lang.t(bp.category) + "]", Draw.accent());
        switch (stage) {
            case SELECT_A -> {
                add(lines, colors, Lang.t("Шаг 1/3: выбери зону"), Draw.WHITE);
                add(lines, colors, "[" + kc + Lang.t("] - первый угол (блок под прицелом)"), yellow);
                add(lines, colors, "[" + kr + Lang.t("] - повернуть (") + rotate.get() + ")", Draw.GRAY);
                add(lines, colors, Lang.t("Размер: ") + width(bp, rot) + " x " + depth(bp, rot) + Lang.t(", высота ") + bp.sy, Draw.GRAY);
                for (String s : wrap(Lang.t(bp.description), 46)) add(lines, colors, s, Draw.GRAY);
            }
            case SELECT_B -> {
                BlockPos look = lookBlock(mc);
                BlockPos b = look != null ? look : cornerA;
                int zx = Math.abs(cornerA.getX() - b.getX()) + 1, zz = Math.abs(cornerA.getZ() - b.getZ()) + 1;
                boolean fit = fits(bp, rot, cornerA, b) || fits(bp, (rot + 1) % 4, cornerA, b);
                add(lines, colors, Lang.t("Шаг 2/3: второй угол зоны"), Draw.WHITE);
                add(lines, colors, Lang.t("Зона ") + zx + " x " + zz + Lang.t(", нужно ") + width(bp, rot) + " x " + depth(bp, rot), fit ? ok : bad);
                add(lines, colors, "[" + kc + Lang.t("] - второй угол, [") + kx + Lang.t("] - отмена"), yellow);
            }
            case CONFIRM -> {
                add(lines, colors, Lang.t("Шаг 3/3: проверь и начни"), Draw.WHITE);
                add(lines, colors, "[" + kc + Lang.t("] - начать стройку, [") + kr + Lang.t("] - повернуть, [") + kx + Lang.t("] - заново"), yellow);
                if (alignedTo != null) add(lines, colors, Lang.t("Выровнено по спавнеру ") + alignedTo.toShortString(), ok);
                if (chunkAligned != null) add(lines, colors, Lang.t("Центр на стыке 4 чанков: ") + chunkAligned.getX() + ", " + chunkAligned.getZ() + Lang.t(" (розовая линия)"), ok);
                if (existingMissing > 0) add(lines, colors, Lang.t("Нет спавнера в нужном месте - выдели зону вокруг данжа"), bad);
                if (wrong > 0) add(lines, colors, Lang.t("Мешают/не так стоят: ") + wrong + Lang.t(" блоков (красные)"), bad);
                add(lines, colors, Lang.t("Уже на месте: ") + done + Lang.t(" из ") + total, done > 0 ? ok : Draw.GRAY);
                materialLines(mc, lines, colors, 8);
                add(lines, colors, Lang.t("Как строить:"), Draw.accent());
                for (String s : bp.guide) for (String w : wrap(Lang.t(s), 52)) add(lines, colors, w, Draw.GRAY);
            }
            case BUILD -> {
                int pct = total == 0 ? 100 : done * 100 / total;
                add(lines, colors, Lang.t("Прогресс: ") + done + " / " + total + " (" + pct + "%)", Draw.WHITE);
                if (current != null) {
                    boolean dig = current.st == St.WRONG;
                    String what = current.spec.match() == Blueprint.Match.AIR ? Lang.t("Убери блок") : dig ? Lang.t("Замени: ") + current.spec.displayName() : Lang.t("Поставь: ") + current.spec.displayName();
                    add(lines, colors, what + Lang.t("  (слой ") + (current.layer + 1) + "/" + bp.sy + ")", dig ? bad : yellow);
                    if (dig && current.spec.match() != Blueprint.Match.AIR) {
                        BlockState have = mc.level.getBlockState(current.pos);
                        if (have.is(current.want.getBlock())) add(lines, colors, Lang.t("Блок тот, но повёрнут не так - сломай и поставь заново"), bad);
                        else add(lines, colors, Lang.t("Сейчас там: ") + have.getBlock().getName().getString(), bad);
                    }
                    String[] hint = Blueprint.placeHint(current.spec, current.want);
                    if (hint != null) for (String h : hint) for (String w : wrap(Lang.t(h), 52)) add(lines, colors, w, Draw.WHITE);
                    BlockPos p = current.pos;
                    Vec3 me = mc.player.position();
                    double dist = Vec3.atCenterOf(p).distanceTo(me);
                    int dy = p.getY() - mc.player.blockPosition().getY();
                    add(lines, colors, Lang.t("Где: ") + p.getX() + " " + p.getY() + " " + p.getZ() + "  (" + (int) dist + Lang.t(" бл.")
                            + (dy > 0 ? Lang.t(", выше на ") + dy : dy < 0 ? Lang.t(", ниже на ") + (-dy) : "") + ")", Draw.GRAY);
                    if (!upcoming.isEmpty()) {
                        StringBuilder sb = new StringBuilder(Lang.t("Дальше: "));
                        for (int i = 0; i < Math.min(3, upcoming.size()); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(upcoming.get(i).spec.displayName());
                        }
                        add(lines, colors, sb.toString(), Draw.GRAY);
                    }
                }
                add(lines, colors, "[" + kc + Lang.t("] - пропустить шаг, [") + kx + Lang.t("] - пауза"), Draw.GRAY);
                materialLines(mc, lines, colors, 6);
            }
            case DONE -> {
                add(lines, colors, Lang.t("Готово! Постройка завершена."), ok);
                for (String s : bp.guide) for (String w : wrap(Lang.t(s), 52)) add(lines, colors, w, Draw.GRAY);
                add(lines, colors, "[" + kc + Lang.t("] - новая постройка"), yellow);
            }
        }
        if (!message.isEmpty() && System.currentTimeMillis() - messageTime < 3000) add(lines, colors, message, 0xFFFFC040);

        int w = 0;
        for (String s : lines) w = Math.max(w, Draw.width(s));
        int x = 4 + panelX.asInt(), y = 120 + panelY.asInt();
        int h = lines.size() * 10 + 6 + (stage == Stage.BUILD ? 5 : 0);
        Draw.panel(g, x, y, x + w + 12, y + h, Draw.bg());
        Draw.rect(g, x, y, x + 2, y + h, Draw.accent());
        int yy = y + 4;
        for (int i = 0; i < lines.size(); i++) {
            Draw.text(g, lines.get(i), x + 7, yy, colors.get(i));
            yy += 10;
            if (i == 1 && stage == Stage.BUILD) {
                int bw = w;
                float f = total == 0 ? 1f : (float) done / total;
                Draw.rect(g, x + 7, yy, x + 7 + bw, yy + 3, 0x60000000);
                Draw.rect(g, x + 7, yy, x + 7 + (int) (bw * f), yy + 3, Draw.accent());
                yy += 5;
            }
        }

        // подписи шагов в мире
        if (stage == Stage.BUILD && current != null) {
            double[] o = new double[3];
            Vec3 c = Vec3.atCenterOf(current.pos);
            if (Projection.project(c.x, c.y + 0.9, c.z, o) && (walls.get() || visible(mc, current.pos))) {
                String s = "▶ " + current.spec.displayName();
                int tw = Draw.width(s);
                Draw.rect(g, (int) o[0] - tw / 2 - 3, (int) o[1] - 2, (int) o[0] + tw / 2 + 3, (int) o[1] + 10, 0xB0000000);
                Draw.textCentered(g, s, (int) o[0], (int) o[1], current.st == St.WRONG ? bad : yellow);
            } else {
                int sw = mc.getWindow().getGuiScaledWidth();
                Draw.textCentered(g, Lang.t("Следующий блок у тебя за спиной"), sw / 2, mc.getWindow().getGuiScaledHeight() / 2 + 30, yellow);
            }
            for (int i = 0; i < upcoming.size(); i++) {
                Vec3 u = Vec3.atCenterOf(upcoming.get(i).pos);
                if (Projection.project(u.x, u.y, u.z, o)) Draw.textCentered(g, String.valueOf(i + 2), (int) o[0], (int) o[1] - 4, 0xFF40D0FF);
            }
        }
    }

    private static boolean visible(Minecraft mc, BlockPos pos) {
        Vec3 eye = mc.player.getEyePosition();
        BlockHitResult r = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, Vec3.atCenterOf(pos),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        return r.getType() == HitResult.Type.MISS || r.getBlockPos().equals(pos) || r.getBlockPos().distManhattan(pos) <= 1;
    }

    private void materialLines(Minecraft mc, List<String> lines, List<Integer> colors, int limit) {
        Map<String, int[]> need = materials(mc);
        if (need.isEmpty()) return;
        add(lines, colors, Lang.t("Материалы (нужно / есть):"), Draw.accent());
        Iterator<Map.Entry<String, int[]>> it = need.entrySet().stream()
                .sorted((a, b) -> b.getValue()[0] - a.getValue()[0]).iterator();
        int n = 0;
        while (it.hasNext() && n++ < limit) {
            Map.Entry<String, int[]> e = it.next();
            int[] v = e.getValue();
            boolean enough = v[1] >= v[0];
            String haveS = v[1] >= 999 ? Lang.t("есть") : String.valueOf(v[1]);
            add(lines, colors, (enough ? "✔ " : "✘ ") + e.getKey() + "  " + v[0] + " / " + haveS, enough ? 0xFF7CFFB0 : 0xFFFF6A6A);
        }
        if (need.size() > limit) add(lines, colors, Lang.t("...и ещё ") + (need.size() - limit), Draw.GRAY);
    }

    private static void add(List<String> lines, List<Integer> colors, String s, int c) {
        lines.add(s);
        colors.add(c);
    }

    private static List<String> wrap(String s, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            if (cur.length() + word.length() + 1 > max && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder("  ");
            }
            if (cur.length() > 0 && !cur.toString().equals("  ")) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    @Override
    public void onWorldChange(Minecraft mc) {
        // выбранная зона и проект привязаны к координатам старого мира
        if (stage != Stage.SELECT_A || !cells.isEmpty()) reset();
    }
}

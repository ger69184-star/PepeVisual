package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Навигатор для ботов (AutoBrew): поиск пути A* и движение по нему клавишами.
 * Камера поворачивается плавно (Aim, каждый кадр) и смотрит вперёд по пути, двери открываются взглядом и ПКМ.
 * Понимает плиты, ковры, снег, песок душ и грядки (раньше пол из плит/ковров считался "стеной" и путь не находился),
 * ходит по диагонали, открывает деревянные двери и калитки, спускается до 3 блоков, обходит лаву, огонь и кактусы.
 * Клетка пути = блок, в котором стоят ноги игрока.
 */
public final class Navigator {
    private static final int[][] DIRS = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private record Node(BlockPos pos, Node parent, double g, double f) {}

    private List<BlockPos> path = new ArrayList<>();
    private int idx;
    private Vec3 lastPos;
    private int stuck, doorCooldown, doorTicks;
    private BlockPos doorPos;
    private Vec3 doorPoint;
    public boolean failed;
    /** true - "как человек" (плавная камера через Aim, двери взглядом и ПКМ), false - базовый режим (резкий поворот, как раньше). */
    public boolean human = true;
    /** Множитель скорости камеры в человеческом режиме. */
    public double camSpeed = 1.0;

    public boolean hasPath() {
        return !path.isEmpty();
    }

    public List<BlockPos> path() {
        return path;
    }

    public int index() {
        return idx;
    }

    public void clear() {
        path = new ArrayList<>();
        idx = 0;
        stuck = 0;
        failed = false;
        lastPos = null;
    }

    // ---------- проходимость ----------

    private static boolean openable(BlockState s) {
        if (s.getBlock() instanceof DoorBlock d) return d.type().canOpenByHand();
        return s.getBlock() instanceof FenceGateBlock;
    }

    private static boolean hazard(BlockState s) {
        return s.getFluidState().is(FluidTags.LAVA) || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.CAMPFIRE)
                || s.is(Blocks.SOUL_CAMPFIRE) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.COBWEB) || s.is(Blocks.CACTUS)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.WITHER_ROSE);
    }

    /** Через блок можно пройти телом (пусто, трава, открытая/открываемая дверь). */
    private static boolean passable(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (hazard(s)) return false;
        if (openable(s)) return true;
        return s.getCollisionShape(level, pos).isEmpty();
    }

    private static double top(Level level, BlockPos pos) {
        VoxelShape sh = level.getBlockState(pos).getCollisionShape(level, pos);
        return sh.isEmpty() ? Double.NaN : sh.max(Direction.Axis.Y);
    }

    /**
     * Высота пола в клетке (абсолютная Y) или NaN, если стоять нельзя.
     * Клетка с низким блоком (ковёр, плита, снег) - пол внутри неё; пустая клетка - пол на блоке снизу.
     */
    public static double floorY(Level level, BlockPos pos) {
        BlockState self = level.getBlockState(pos);
        if (hazard(self)) return Double.NaN;
        double own = openable(self) ? Double.NaN : top(level, pos);
        double floor;
        if (!Double.isNaN(own)) {
            if (own > 0.5) return Double.NaN;
            floor = pos.getY() + own;
            if (own > 0.2 && !passable(level, pos.above(2))) return Double.NaN;
        } else {
            BlockPos below = pos.below();
            BlockState b = level.getBlockState(below);
            double bt = top(level, below);
            if (Double.isNaN(bt) || bt <= 0.5 || bt > 1.0) return Double.NaN; // нет пола / пол в нижней клетке / забор
            if (b.is(Blocks.MAGMA_BLOCK) || b.is(Blocks.CACTUS) || hazard(b)) return Double.NaN;
            floor = below.getY() + bt;
        }
        if (!passable(level, pos.above())) return Double.NaN;
        return floor;
    }

    private static boolean canStand(Level level, BlockPos pos) {
        return !Double.isNaN(floorY(level, pos));
    }

    /** Клетка, в которой реально стоит игрок (на песке душ, грядке и т.п. blockPosition указывает на блок пола). */
    public static BlockPos standingCell(Level level, LocalPlayer p) {
        BlockPos b = p.blockPosition();
        if (canStand(level, b)) return b;
        if (canStand(level, b.above())) return b.above();
        if (canStand(level, b.below())) return b.below();
        return b;
    }

    /** Ищет путь от start до ближайшей клетки, подходящей под goal. target - примерная цель для эвристики. */
    public boolean findPath(Level level, BlockPos start, BlockPos target, Predicate<BlockPos> goal, int maxNodes) {
        clear();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Map<Long, Double> best = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        Map<Long, Double> floors = new HashMap<>();
        open.add(new Node(start, null, 0, h(start, target)));
        best.put(start.asLong(), 0.0);
        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            Node cur = open.poll();
            long key = cur.pos.asLong();
            if (!closed.add(key)) continue;
            expanded++;
            if (goal.test(cur.pos)) {
                List<BlockPos> p = new ArrayList<>();
                for (Node n = cur; n != null; n = n.parent) p.add(n.pos);
                Collections.reverse(p);
                path = p;
                idx = p.size() > 1 ? 1 : 0;
                return true;
            }
            double curFloor = floor(level, floors, cur.pos);
            if (Double.isNaN(curFloor)) curFloor = cur.pos.getY();
            boolean headroom = passable(level, cur.pos.above(2));
            for (int[] d : DIRS) {
                boolean diag = d[0] != 0 && d[1] != 0;
                BlockPos col = cur.pos.offset(d[0], 0, d[1]);
                if (diag) {
                    // по диагонали только если обе соседние клетки свободны (не срезаем углы)
                    BlockPos a = cur.pos.offset(d[0], 0, 0), b = cur.pos.offset(0, 0, d[1]);
                    if (!passable(level, a) || !passable(level, a.above()) || !passable(level, b) || !passable(level, b.above())) continue;
                }
                // кандидаты по высоте: +1, 0, -1..-3
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos n = col.above(dy);
                    double f = floor(level, floors, n);
                    if (Double.isNaN(f)) {
                        if (dy <= 0 && !passable(level, n)) break; // ниже - стена, падать некуда
                        continue;
                    }
                    double rise = f - curFloor;
                    if (rise > 1.25) break;
                    if (rise > 0.6 && (!headroom || diag)) break; // для прыжка нужно место над головой
                    if (rise < -0.6 && (!passable(level, col) || !passable(level, col.above()))) break; // падаем через свободный столб
                    double cost = (diag ? 1.414 : 1.0) + (rise > 0.6 ? 0.9 : 0) + (rise < -0.6 ? -rise * 0.5 : 0);
                    if (openable(level.getBlockState(n)) || openable(level.getBlockState(n.above()))) cost += 2.0;
                    push(open, best, closed, cur, n, cost, target);
                    break;
                }
            }
        }
        failed = true;
        return false;
    }

    private static double floor(Level level, Map<Long, Double> cache, BlockPos pos) {
        return cache.computeIfAbsent(pos.asLong(), k -> floorY(level, pos));
    }

    private static double h(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX()), dz = Math.abs(a.getZ() - b.getZ());
        return Math.max(dx, dz) + 0.414 * Math.min(dx, dz) + Math.abs(a.getY() - b.getY()) * 1.2;
    }

    private static void push(PriorityQueue<Node> open, Map<Long, Double> best, Set<Long> closed, Node parent, BlockPos pos, double cost, BlockPos target) {
        long key = pos.asLong();
        if (closed.contains(key)) return;
        double g = parent.g + cost;
        Double old = best.get(key);
        if (old != null && old <= g) return;
        best.put(key, g);
        open.add(new Node(pos, parent, g, g + h(pos, target)));
    }

    // ---------- движение ----------

    /** Один тик движения по пути. true - дошли до конца. */
    public boolean tick(Minecraft mc, LocalPlayer p) {
        if (path.isEmpty()) return true;
        if (idx >= path.size()) {
            stop(mc);
            return true;
        }
        Level level = mc.level;
        BlockPos node = path.get(idx);
        double fy = floorY(level, node);
        if (Double.isNaN(fy)) fy = node.getY();
        Vec3 c = Vec3.atBottomCenterOf(node);
        double dx = c.x - p.getX(), dz = c.z - p.getZ();
        double hd = Math.sqrt(dx * dx + dz * dz);
        double dy = fy - p.getY();
        boolean last = idx == path.size() - 1;
        if (hd < (last ? 0.3 : 0.5) && Math.abs(dy) < 1.3) {
            idx++;
            if (idx >= path.size()) {
                stop(mc);
                return true;
            }
            return tick(mc, p);
        }

        if (!human) {
            // базовый режим (как раньше): поворот до 30 градусов за тик, двери открываются без взгляда
            openDoorsBasic(mc, p);
            float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float diff = Mth.wrapDegrees(target - p.getYRot());
            p.setYRot(p.getYRot() + Mth.clamp(diff, -30f, 30f));
            mc.options.keyUp.setDown(Math.abs(diff) < 50f);
            boolean jump = p.onGround() && ((dy > 0.6 && hd < 1.6) || (p.horizontalCollision && Math.abs(diff) < 30f));
            mc.options.keyJump.setDown(jump);
            return stuckCheck(mc, p);
        }
        if (openDoors(mc, p)) {
            mc.options.keyUp.setDown(false);
            mc.options.keyJump.setDown(false);
            return false;
        }

        // смотрим не на клетку под ногами, а чуть вперёд по пути - поворот плавный, как у человека (Aim крутит камеру каждый кадр)
        Vec3 aheadC = c;
        if (hd < 1.4 && idx + 1 < path.size()) aheadC = Vec3.atBottomCenterOf(path.get(idx + 1));
        double ax = aheadC.x - p.getX(), az = aheadC.z - p.getZ();
        double al = Math.max(1e-3, Math.sqrt(ax * ax + az * az)), far = Math.max(3.0, al);
        Aim.look(this, new Vec3(p.getX() + ax / al * far, p.getEyeY() - 0.75 + Mth.clamp(dy, -1, 1) * 0.5, p.getZ() + az / al * far), camSpeed);

        float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float diff = Mth.wrapDegrees(target - p.getYRot());
        mc.options.keyUp.setDown(Math.abs(diff) < 45f);
        boolean jump = p.onGround() && ((dy > 0.6 && hd < 1.6) || (p.horizontalCollision && Math.abs(diff) < 30f));
        mc.options.keyJump.setDown(jump);

        return stuckCheck(mc, p);
    }

    /** Застревание: 2 секунды почти на месте - путь не удался. */
    private boolean stuckCheck(Minecraft mc, LocalPlayer p) {
        Vec3 pos = p.position();
        if (lastPos != null && lastPos.distanceToSqr(pos) < 0.0004) stuck++;
        else stuck = 0;
        lastPos = pos;
        if (stuck > 40) {
            stop(mc);
            failed = true;
        }
        return false;
    }

    /** Базовый режим: открыть закрытую дверь/калитку рядом на пути сразу, без взгляда (как раньше). */
    private void openDoorsBasic(Minecraft mc, LocalPlayer p) {
        if (doorCooldown > 0) {
            doorCooldown--;
            return;
        }
        for (int i = idx; i < Math.min(path.size(), idx + 2); i++) {
            for (BlockPos b : new BlockPos[]{path.get(i), path.get(i).above()}) {
                BlockState s = mc.level.getBlockState(b);
                if (!openable(s)) continue;
                boolean open = s.getBlock() instanceof DoorBlock d ? d.isOpen(s) : s.getValue(FenceGateBlock.OPEN);
                if (open) continue;
                if (p.getEyePosition().distanceTo(Vec3.atCenterOf(b)) > 3.5) continue;
                mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(b), Direction.UP, b, false));
                doorCooldown = 10;
                return;
            }
        }
    }

    /**
     * Закрытая дверь/калитка на ближайших клетках пути: смотрим на неё и жмём ПКМ (как игрок).
     * true - сейчас занимаемся дверью, идти пока не надо.
     */
    private boolean openDoors(Minecraft mc, LocalPlayer p) {
        if (doorCooldown > 0) {
            doorCooldown--;
            return false;
        }
        for (int i = idx; i < Math.min(path.size(), idx + 2); i++) {
            for (BlockPos b : new BlockPos[]{path.get(i), path.get(i).above()}) {
                BlockState s = mc.level.getBlockState(b);
                if (!openable(s)) continue;
                boolean open = s.getBlock() instanceof DoorBlock d ? d.isOpen(s) : s.getValue(FenceGateBlock.OPEN);
                if (open) continue;
                if (p.getEyePosition().distanceTo(Vec3.atCenterOf(b)) > 3.5) continue;
                if (doorPoint == null || !doorPos.equals(b)) {
                    doorPos = b.immutable();
                    doorPoint = Aim.pointOn(mc.level, p, b, 0.35, 0.75);
                    if (doorPoint == null) doorPoint = Vec3.atCenterOf(b);
                    doorTicks = 0;
                }
                Aim.look(this, doorPoint, camSpeed);
                boolean onDoor = Aim.crosshairOn(mc, b) || (openable(mc.level.getBlockState(b.above())) && Aim.crosshairOn(mc, b.above()))
                        || (openable(mc.level.getBlockState(b.below())) && Aim.crosshairOn(mc, b.below()));
                if (onDoor && Aim.settled(this) && ++doorTicks > 1) {
                    Keys.click(mc.options.keyUse);
                    Aim.freeze(2);
                    doorCooldown = Human.range(8, 14);
                    doorPoint = null;
                } else if (doorTicks++ > 40) {
                    // так и не навели - откроем как раньше, чтобы не застрять навсегда
                    mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(doorPoint, Direction.UP, b, false));
                    doorCooldown = 10;
                    doorPoint = null;
                }
                return true;
            }
        }
        doorPoint = null;
        return false;
    }

    public void stop(Minecraft mc) {
        // если игрок сам держит W/пробел - не "отжимаем" их
        Keys.release(mc, mc.options.keyUp);
        Keys.release(mc, mc.options.keyJump);
        Aim.release(this);
        stuck = 0;
        lastPos = null;
    }
}

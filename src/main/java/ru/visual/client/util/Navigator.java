package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
 * Простой навигатор: поиск пути A* по клеткам (шаг, подъём на 1, спуск до 3) и движение по нему
 * через клавиши и поворот персонажа. Не открывает двери и не плавает, обходит лаву и кактусы.
 */
public final class Navigator {
    private static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private record Node(BlockPos pos, Node parent, double g, double f) {}

    private List<BlockPos> path = new ArrayList<>();
    private int idx;
    private Vec3 lastPos;
    private int stuck;
    private int repath;
    public boolean failed;

    public boolean hasPath() {
        return !path.isEmpty();
    }

    public void clear() {
        path = new ArrayList<>();
        idx = 0;
        stuck = 0;
        failed = false;
        lastPos = null;
    }

    // ---------- проходимость ----------

    private static boolean passable(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!s.getCollisionShape(level, pos).isEmpty()) return false;
        return !s.getFluidState().is(FluidTags.LAVA) && !s.is(Blocks.FIRE) && !s.is(Blocks.SOUL_FIRE)
                && !s.is(Blocks.CAMPFIRE) && !s.is(Blocks.SWEET_BERRY_BUSH) && !s.is(Blocks.COBWEB) && !s.is(Blocks.CACTUS);
    }

    private static boolean canStand(Level level, BlockPos pos) {
        if (!passable(level, pos) || !passable(level, pos.above())) return false;
        BlockPos below = pos.below();
        BlockState b = level.getBlockState(below);
        if (b.getCollisionShape(level, below).isEmpty()) return false;
        return !b.is(Blocks.MAGMA_BLOCK) && !b.is(Blocks.CACTUS) && !b.is(Blocks.CAMPFIRE) && !b.is(Blocks.SOUL_CAMPFIRE);
    }

    /** Ищет путь от start до ближайшей клетки, подходящей под goal. target - примерная цель для эвристики. */
    public boolean findPath(Level level, BlockPos start, BlockPos target, Predicate<BlockPos> goal, int maxNodes) {
        clear();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Map<Long, Double> best = new HashMap<>();
        Set<Long> closed = new HashSet<>();
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
            for (Direction d : DIRS) {
                BlockPos n = cur.pos.relative(d);
                // ровно
                if (canStand(level, n)) {
                    push(open, best, closed, cur, n, 1.0, target);
                    continue;
                }
                // подъём на 1 блок (нужно место над головой)
                if (passable(level, cur.pos.above(2)) && canStand(level, n.above())) {
                    push(open, best, closed, cur, n.above(), 1.9, target);
                    continue;
                }
                // спуск до 3 блоков
                if (passable(level, n) && passable(level, n.above())) {
                    for (int drop = 1; drop <= 3; drop++) {
                        BlockPos m = n.below(drop);
                        if (canStand(level, m)) {
                            push(open, best, closed, cur, m, 1.0 + 0.6 * drop, target);
                            break;
                        }
                        if (!passable(level, m)) break;
                    }
                }
            }
        }
        failed = true;
        return false;
    }

    private static double h(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()) + Math.abs(a.getY() - b.getY()) * 1.5;
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
        BlockPos node = path.get(idx);
        Vec3 c = Vec3.atBottomCenterOf(node);
        double dx = c.x - p.getX(), dz = c.z - p.getZ();
        double hd = Math.sqrt(dx * dx + dz * dz);
        double dy = c.y - p.getY();
        if (hd < 0.35 && Math.abs(dy) < 1.3) {
            idx++;
            if (idx >= path.size()) {
                stop(mc);
                return true;
            }
            node = path.get(idx);
            c = Vec3.atBottomCenterOf(node);
            dx = c.x - p.getX();
            dz = c.z - p.getZ();
            dy = c.y - p.getY();
        }

        float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float diff = Mth.wrapDegrees(target - p.getYRot());
        p.setYRot(p.getYRot() + Mth.clamp(diff, -35f, 35f));
        mc.options.keyUp.setDown(Math.abs(diff) < 55f);
        boolean jump = (dy > 0.4 && p.onGround()) || (p.horizontalCollision && p.onGround());
        mc.options.keyJump.setDown(jump);

        // застревание
        Vec3 pos = p.position();
        if (lastPos != null && lastPos.distanceToSqr(pos) < 0.0004) stuck++;
        else stuck = 0;
        lastPos = pos;
        if (stuck > 40) {
            stop(mc);
            failed = true;
            return false;
        }
        return false;
    }

    public void stop(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        stuck = 0;
        lastPos = null;
    }
}

package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Плавный "человеческий" поворот камеры для помощников (AutoFarm, AutoBrew, ходьба).
 * <ul>
 *     <li>Камера поворачивается каждый кадр, а не рывком раз в тик: быстро в начале и мягко в конце (как рука с мышкой).</li>
 *     <li>Поворот идёт целыми "пикселями мыши" с учётом твоей чувствительности - как будто двигаешь мышь сам.</li>
 *     <li>Скорость, лёгкое дрожание и изгиб траектории каждый раз немного разные; смотрим не ровно в центр блока, а в случайную точку на нём.</li>
 *     <li>Если игрок сам двигает мышь - помощник сразу уступает камеру.</li>
 * </ul>
 * Модуль вызывает {@link #look} каждый тик, пока ему нужна камера; перестал вызывать - камера освобождается сама.
 */
public final class Aim {
    private static Object owner;
    private static Vec3 point;
    private static long ticks, lastRequest, frozenUntil;
    private static double speed = 10, maxSpeed = 320, pitchLag = 0.85, phase;
    private static double remX, remY;
    private static long lastNanos;
    private static double lastErr = 999, angVel;
    private static int userCooldown;
    private static double mul = 1.0;
    /** Уступать ли камеру игроку, когда он сам двигает мышь (настройка текущего помощника). */
    public static boolean yieldToUser = true;

    private Aim() {}

    /** Смотреть на точку (каждый тик, пока нужно). */
    public static void look(Object who, Vec3 p) {
        look(who, p, 1.0);
    }

    /** Смотреть на точку; speedMul - множитель скорости поворота (настройка "Скорость камеры"). */
    public static void look(Object who, Vec3 p, double speedMul) {
        if (p == null) return;
        mul = Math.max(0.2, speedMul);
        if (who != owner || point == null || point.distanceToSqr(p) > 4.0) {
            // новая цель - "рука" каждый раз чуть другая
            speed = Human.rnd(7.0, 12.0);
            maxSpeed = Human.rnd(240, 420);
            pitchLag = Human.rnd(0.72, 0.95);
            phase = Human.rnd(0, 1000);
            lastErr = 999;
        }
        owner = who;
        point = p;
        lastRequest = ticks;
    }

    public static void release(Object who) {
        if (owner == who) {
            owner = null;
            point = null;
            angVel = 0;
        }
    }

    /** Камерой сейчас управляет кто-то из помощников. */
    public static boolean active() {
        return owner != null && point != null;
    }

    /** Игрок сам недавно двигал мышь. */
    public static boolean userActive() {
        return userCooldown > 0;
    }

    /** Ненадолго замереть (после клика рука не дёргается сразу дальше). */
    public static void freeze(int t) {
        frozenUntil = Math.max(frozenUntil, ticks + t);
    }

    /** Взгляд почти дошёл до цели и камера уже не летит. */
    public static boolean settled(Object who) {
        return owner == who && point != null && lastErr < 4.0 && angVel < 120;
    }

    /** Угол (в градусах) между взглядом игрока и направлением на точку - для выбора "ближайшего к прицелу" блока. */
    public static double angleTo(LocalPlayer p, Vec3 target) {
        Vec3 eye = p.getEyePosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        double ey = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)) - p.getYRot());
        double ep = -Math.toDegrees(Math.atan2(dy, h)) - p.getXRot();
        return Math.sqrt(ey * ey + ep * ep);
    }

    // ---------------------------------------------------------------- точки на блоках

    /**
     * Случайная видимая точка на блоке (в пределах его формы), до которой не мешают другие блоки.
     * yMin/yMax - доля высоты формы (0 - низ, 1 - верх). null - блок не виден.
     */
    public static Vec3 pointOn(Level level, LocalPlayer p, BlockPos pos, double yMin, double yMax) {
        BlockState st = level.getBlockState(pos);
        VoxelShape sh = st.getShape(level, pos);
        AABB b = sh.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : sh.bounds();
        Vec3 eye = p.getEyePosition();
        for (int i = 0; i < 7; i++) {
            double fx = i == 6 ? 0.5 : Human.rnd(0.28, 0.72), fz = i == 6 ? 0.5 : Human.rnd(0.28, 0.72);
            double fy = i == 6 ? (yMin + yMax) / 2 : Human.rnd(yMin, yMax);
            Vec3 v = new Vec3(pos.getX() + Mth.lerp(fx, b.minX, b.maxX), pos.getY() + Mth.lerp(fy, b.minY, b.maxY),
                    pos.getZ() + Mth.lerp(fz, b.minZ, b.maxZ));
            if (visible(level, p, eye, v, pos)) return v;
        }
        return null;
    }

    private static boolean visible(Level level, LocalPlayer p, Vec3 eye, Vec3 v, BlockPos pos) {
        Vec3 dir = v.subtract(eye);
        if (dir.lengthSqr() < 1e-6) return true;
        Vec3 end = v.add(dir.normalize().scale(0.25));
        BlockHitResult r = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(pos);
    }

    /** Прицел игрока (обычный, ванильный) сейчас на этом блоке. */
    public static boolean crosshairOn(Minecraft mc, BlockPos pos) {
        return mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(pos);
    }

    // ---------------------------------------------------------------- тик и кадр

    /** Каждый тик (из VisualClient). */
    public static void tick() {
        ticks++;
        if (userCooldown > 0) userCooldown--;
        if (owner != null && ticks - lastRequest > 3) {
            owner = null;
            point = null;
            angVel = 0;
        }
    }

    /** Движение мыши игрока за кадр (из MouseHandlerMixin). */
    public static void onMouse(double dx, double dy) {
        if (Math.abs(dx) + Math.abs(dy) > 1.0) userCooldown = 14;
    }

    /** Каждый кадр (из MouseHandlerMixin, сразу после обработки мыши). */
    public static void frame(Minecraft mc) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        LocalPlayer p = mc.player;
        if (p == null || point == null || mc.screen != null || mc.isPaused() || (userCooldown > 0 && yieldToUser) || dt <= 0 || ticks < frozenUntil) {
            angVel = 0;
            return;
        }
        Vec3 eye = p.getEyePosition();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        double ty = Math.toDegrees(Math.atan2(-dx, dz));
        double tp = -Math.toDegrees(Math.atan2(dy, h));
        double ey = Mth.wrapDegrees(ty - p.getYRot()), ep = tp - p.getXRot();
        double err = Math.sqrt(ey * ey + ep * ep);
        lastErr = err;
        if (err < 0.08) {
            angVel = 0;
            return;
        }
        // быстро в начале, мягко в конце; минимальная скорость - чтобы не "доползать" вечно
        double step = err * (1 - Math.exp(-speed * mul * dt));
        step = Math.max(step, Math.min(err, 14 * mul * dt));
        step = Math.min(step, maxSpeed * mul * dt);
        double t = now / 1e9 + phase;
        double wobble = 1 + 0.10 * Math.sin(t * 8.7) + 0.05 * Math.sin(t * 21.3);
        double my = ey / err * step * wobble;
        double mp = ep / err * step * pitchLag * wobble;
        // лёгкий изгиб траектории - рука не ведёт мышь по линейке
        double curve = 0.10 * Math.sin(t * 3.1) * step;
        my += -ep / err * curve;
        mp += ey / err * curve;

        // в "пиксели мыши" с учётом чувствительности (как MouseHandler.turnPlayer)
        double s = mc.options.sensitivity().get() * 0.6 + 0.2;
        double sens = s * s * s * 8.0;
        double perPixel = sens * 0.15;
        remX += my / perPixel;
        remY += mp / perPixel;
        long px = Math.round(remX), py = Math.round(remY);
        remX -= px;
        remY -= py;
        if (px != 0 || py != 0) p.turn(px * sens, py * sens);
        angVel = step / dt;
    }
}

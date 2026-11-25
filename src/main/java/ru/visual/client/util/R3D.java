package ru.visual.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Рисование прямо в 3D-мире (линии, полупрозрачные коробки) - красивее и точнее, чем проекция на HUD.
 * Работает только внутри {@link ru.visual.client.module.Module#onWorld}.
 */
public final class R3D {
    private static PoseStack ps;
    private static MultiBufferSource buf;
    private static Vec3 cam = Vec3.ZERO;

    private R3D() {}

    public static void begin(WorldRenderContext ctx) {
        ps = ctx.matrices();
        buf = ctx.consumers();
        Vec3 c = ctx.worldState().cameraRenderState.pos;
        cam = c != null ? c : Vec3.ZERO;
    }

    public static void end() {
        ps = null;
        buf = null;
    }

    public static boolean ready() {
        return ps != null && buf != null;
    }

    public static Vec3 camera() {
        return cam;
    }

    /** Отрезок a-b (цвет ARGB, толщина в пикселях). */
    public static void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float width) {
        if (!ready()) return;
        VertexConsumer vc = buf.getBuffer(RenderTypes.lines());
        PoseStack.Pose pose = ps.last();
        float ax = (float) (x1 - cam.x), ay = (float) (y1 - cam.y), az = (float) (z1 - cam.z);
        float bx = (float) (x2 - cam.x), by = (float) (y2 - cam.y), bz = (float) (z2 - cam.z);
        Vector3f n = new Vector3f(bx - ax, by - ay, bz - az);
        if (n.lengthSquared() < 1e-8f) return;
        n.normalize();
        vc.addVertex(pose, ax, ay, az).setColor(argb).setNormal(pose, n).setLineWidth(width);
        vc.addVertex(pose, bx, by, bz).setColor(argb).setNormal(pose, n).setLineWidth(width);
    }

    public static void line(Vec3 a, Vec3 b, int argb, float width) {
        line(a.x, a.y, a.z, b.x, b.y, b.z, argb, width);
    }

    /** Рёбра коробки. */
    public static void boxOutline(AABB b, int argb, float width) {
        double x1 = b.minX, y1 = b.minY, z1 = b.minZ, x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;
        line(x1, y1, z1, x2, y1, z1, argb, width);
        line(x2, y1, z1, x2, y1, z2, argb, width);
        line(x2, y1, z2, x1, y1, z2, argb, width);
        line(x1, y1, z2, x1, y1, z1, argb, width);
        line(x1, y2, z1, x2, y2, z1, argb, width);
        line(x2, y2, z1, x2, y2, z2, argb, width);
        line(x2, y2, z2, x1, y2, z2, argb, width);
        line(x1, y2, z2, x1, y2, z1, argb, width);
        line(x1, y1, z1, x1, y2, z1, argb, width);
        line(x2, y1, z1, x2, y2, z1, argb, width);
        line(x2, y1, z2, x2, y2, z2, argb, width);
        line(x1, y1, z2, x1, y2, z2, argb, width);
    }

    public static void blockOutline(BlockPos p, int argb, float width) {
        boxOutline(new AABB(p).inflate(0.002), argb, width);
    }

    /** Полупрозрачная заливка коробки. */
    public static void boxFilled(AABB b, int argb) {
        if (!ready()) return;
        VertexConsumer v = buf.getBuffer(RenderTypes.debugFilledBox());
        Matrix4f m = ps.last().pose();
        float x1 = (float) (b.minX - cam.x), y1 = (float) (b.minY - cam.y), z1 = (float) (b.minZ - cam.z);
        float x2 = (float) (b.maxX - cam.x), y2 = (float) (b.maxY - cam.y), z2 = (float) (b.maxZ - cam.z);
        // -Z
        v.addVertex(m, x1, y1, z1).setColor(argb); v.addVertex(m, x1, y2, z1).setColor(argb);
        v.addVertex(m, x2, y2, z1).setColor(argb); v.addVertex(m, x2, y1, z1).setColor(argb);
        // +Z
        v.addVertex(m, x1, y1, z2).setColor(argb); v.addVertex(m, x2, y1, z2).setColor(argb);
        v.addVertex(m, x2, y2, z2).setColor(argb); v.addVertex(m, x1, y2, z2).setColor(argb);
        // -X
        v.addVertex(m, x1, y1, z1).setColor(argb); v.addVertex(m, x1, y1, z2).setColor(argb);
        v.addVertex(m, x1, y2, z2).setColor(argb); v.addVertex(m, x1, y2, z1).setColor(argb);
        // +X
        v.addVertex(m, x2, y1, z1).setColor(argb); v.addVertex(m, x2, y2, z1).setColor(argb);
        v.addVertex(m, x2, y2, z2).setColor(argb); v.addVertex(m, x2, y1, z2).setColor(argb);
        // низ
        v.addVertex(m, x1, y1, z1).setColor(argb); v.addVertex(m, x2, y1, z1).setColor(argb);
        v.addVertex(m, x2, y1, z2).setColor(argb); v.addVertex(m, x1, y1, z2).setColor(argb);
        // верх
        v.addVertex(m, x1, y2, z1).setColor(argb); v.addVertex(m, x1, y2, z2).setColor(argb);
        v.addVertex(m, x2, y2, z2).setColor(argb); v.addVertex(m, x2, y2, z1).setColor(argb);
    }

    /** Стрелка из центра блока в сторону dir (подсказка "куда смотреть при установке"). */
    public static void arrow(BlockPos p, Direction dir, int argb, float width) {
        Vec3 c = Vec3.atCenterOf(p);
        Vec3 d = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        Vec3 tip = c.add(d.scale(0.75));
        Vec3 tail = c.subtract(d.scale(0.45));
        line(tail, tip, argb, width);
        // перпендикуляр для "крыльев" стрелки
        Vec3 side = dir.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side2 = d.cross(side).normalize();
        Vec3 back = tip.subtract(d.scale(0.3));
        line(tip, back.add(side.scale(0.22)), argb, width);
        line(tip, back.subtract(side.scale(0.22)), argb, width);
        line(tip, back.add(side2.scale(0.22)), argb, width);
        line(tip, back.subtract(side2.scale(0.22)), argb, width);
    }

    /** Горизонтальная окружность. */
    public static void circle(double cx, double cy, double cz, double r, int segments, int argb, float width) {
        double px = cx + r, pz = cz;
        for (int i = 1; i <= segments; i++) {
            double a = Math.PI * 2 * i / segments;
            double nx = cx + Math.cos(a) * r, nz = cz + Math.sin(a) * r;
            line(px, cy, pz, nx, cy, nz, argb, width);
            px = nx;
            pz = nz;
        }
    }
}

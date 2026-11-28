package ru.visual.client.util;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Проекция 3D -> экран (в GUI-координатах).
 * Основной режим: матрицы, которыми игра реально рисовала мир в этом кадре (их ловит LevelRendererMixin).
 * Поэтому учитываются вид от 3-го лица, покачивание камеры, зум, динамический FOV и т.д.
 * Запасной режим (если миксин не сработал): камера + FOV из настроек.
 */
public final class Projection {
    // ---- данные, пойманные из рендера мира ----
    private static final Matrix4f capturedMvp = new Matrix4f();
    private static double capX, capY, capZ;
    private static volatile boolean captured;

    // ---- рабочие данные текущего кадра HUD ----
    private static final Matrix4f mvp = new Matrix4f();
    private static final Vector4f tmp = new Vector4f();
    private static boolean useMatrix;
    private static double ex, ey, ez;
    private static double rx, ry, rz, ux, uy, uz, fx, fy, fz;
    private static double focal, cx, cy, gw, gh;
    private static boolean ready;

    private Projection() {}

    /** Вызывается из LevelRendererMixin в начале отрисовки мира. */
    public static void capture(Vec3 cameraPos, Matrix4f modelView, Matrix4f projection) {
        capturedMvp.set(projection).mul(modelView);
        capX = cameraPos.x;
        capY = cameraPos.y;
        capZ = cameraPos.z;
        captured = true;
    }

    public static void update(Minecraft mc, float pt) {
        if (mc.player == null) {
            ready = false;
            return;
        }
        gw = mc.getWindow().getGuiScaledWidth();
        gh = mc.getWindow().getGuiScaledHeight();
        cx = gw / 2.0;
        cy = gh / 2.0;

        if (captured) {
            captured = false;
            mvp.set(capturedMvp);
            ex = capX;
            ey = capY;
            ez = capZ;
            useMatrix = true;
            ready = true;
            return;
        }

        // Запасной вариант: позиция и углы камеры (работает и от 3-го лица), FOV из настроек
        useMatrix = false;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.position();
        ex = eye.x;
        ey = eye.y;
        ez = eye.z;
        double yaw = Math.toRadians(cam.yRot());
        double pitch = Math.toRadians(cam.xRot());
        double cp = Math.cos(pitch), sp = Math.sin(pitch), cyaw = Math.cos(yaw), syaw = Math.sin(yaw);
        fx = -syaw * cp;
        fy = -sp;
        fz = cyaw * cp;
        rx = -cyaw;
        ry = 0;
        rz = -syaw;
        ux = ry * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;
        double fov = Math.toRadians(mc.options.fov().get());
        focal = (gh / 2.0) / Math.tan(fov / 2.0);
        ready = true;
    }

    public static boolean isReady() {
        return ready;
    }

    /** Позиция камеры (откуда смотрит игрок, с учётом 3-го лица). */
    public static Vec3 eye() {
        return new Vec3(ex, ey, ez);
    }

    /** out = {screenX, screenY, depth}. false если точка за камерой. */
    public static boolean project(double x, double y, double z, double[] out) {
        if (!ready) return false;
        double dx = x - ex, dy = y - ey, dz = z - ez;
        if (useMatrix) {
            tmp.set((float) dx, (float) dy, (float) dz, 1f);
            mvp.transform(tmp);
            float w = tmp.w;
            if (w < 0.05f) return false;
            double ndcX = tmp.x / w, ndcY = tmp.y / w;
            out[0] = (ndcX + 1.0) * 0.5 * gw;
            out[1] = (1.0 - ndcY) * 0.5 * gh;
            out[2] = w;
            return true;
        }
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.05) return false;
        double px = dx * rx + dy * ry + dz * rz;
        double py = dx * ux + dy * uy + dz * uz;
        out[0] = cx + (px / depth) * focal;
        out[1] = cy - (py / depth) * focal;
        out[2] = depth;
        return true;
    }

    /** 2D-прямоугольник вокруг бокса: {minX, minY, maxX, maxY} или null, если часть бокса за камерой. */
    public static double[] boxRect(AABB b) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double[] o = new double[3];
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 2) == 0 ? b.minY : b.maxY;
            double z = (i & 4) == 0 ? b.minZ : b.maxZ;
            if (!project(x, y, z, o)) return null;
            minX = Math.min(minX, o[0]);
            minY = Math.min(minY, o[1]);
            maxX = Math.max(maxX, o[0]);
            maxY = Math.max(maxY, o[1]);
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    public static boolean onScreen(double x, double y, int w, int h) {
        return x > -50 && y > -50 && x < w + 50 && y < h + 50;
    }
}

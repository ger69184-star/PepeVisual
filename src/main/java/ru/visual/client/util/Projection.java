package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Собственная проекция 3D -> экран (в GUI-координатах).
 * Не зависит от внутренностей рендера Minecraft, поэтому переживает его изменения.
 * Камера = глаза игрока (точно работает от первого лица).
 */
public final class Projection {
    private static double ex, ey, ez;
    private static double rx, ry, rz, ux, uy, uz, fx, fy, fz;
    private static double focal, cx, cy;
    private static boolean ready;

    private Projection() {}

    public static void update(Minecraft mc, float pt) {
        LocalPlayer p = mc.player;
        if (p == null) {
            ready = false;
            return;
        }
        Vec3 eye = p.getEyePosition(pt);
        ex = eye.x;
        ey = eye.y;
        ez = eye.z;
        double yaw = Math.toRadians(p.getViewYRot(pt));
        double pitch = Math.toRadians(p.getViewXRot(pt));
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

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        cx = w / 2.0;
        cy = h / 2.0;
        double fov = Math.toRadians(mc.options.fov().get());
        focal = (h / 2.0) / Math.tan(fov / 2.0);
        ready = true;
    }

    public static boolean isReady() {
        return ready;
    }

    public static Vec3 eye() {
        return new Vec3(ex, ey, ez);
    }

    /** out = {screenX, screenY, depth}. false если точка за камерой. */
    public static boolean project(double x, double y, double z, double[] out) {
        if (!ready) return false;
        double dx = x - ex, dy = y - ey, dz = z - ez;
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

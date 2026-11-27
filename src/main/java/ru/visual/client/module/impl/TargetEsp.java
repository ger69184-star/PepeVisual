package ru.visual.client.module.impl;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;
import ru.visual.client.util.R3D;
import ru.visual.client.util.TargetManager;

/**
 * Выделение цели. По умолчанию враг начинает светиться (контур сквозь стены) и получает анимированный круг,
 * как только ты его ударил. Выделение держится, пока ты продолжаешь бить, и плавно гаснет.
 */
public class TargetEsp extends Module implements Config.DefaultOn {
    private static TargetEsp instance;

    private final ModeSetting when = add(new ModeSetting("Когда выделять", "При ударе - только тот, кого ты ударил. Удар или прицел - ещё и тот, на кого смотришь.",
            "При ударе", "При ударе", "Удар или прицел"));
    private final NumSetting hold = add(new NumSetting("Время выделения (сек)", "Сколько секунд после последнего удара враг остаётся выделенным.", 8, 1, 30, 1));
    private final BoolSetting glow = add(new BoolSetting("Свечение", "Ударенный враг светится контуром (видно сквозь стены).", true));
    private final ModeSetting style = add(new ModeSetting("Стиль", "Круг 3D - светящийся круг бегает по телу. Призраки - огоньки кружат вокруг. Точки - старое кольцо из точек.",
            "Круг 3D", "Круг 3D", "Призраки", "Точки", "Нет"));
    private final ModeSetting colorMode = add(new ModeSetting("Цвет", "По HP - от зелёного к красному. Радуга - переливается. Свой - цвет ниже.", "По HP", "По HP", "Радуга", "Свой"));
    private final ColorSetting color = add(new ColorSetting("Свой цвет", "Цвет выделения в режиме 'Свой'.", 0xFF3050));
    private final BoolSetting flash = add(new BoolSetting("Вспышка при ударе", "На мгновение подсвечивает цель белым при каждом ударе.", true));
    private final BoolSetting corners = add(new BoolSetting("Уголки", "Уголки вокруг цели на экране.", true));
    private final BoolSetting label = add(new BoolSetting("Подпись", "Над целью: здоровье и сколько раз ты попал.", false));
    private final NumSetting speed = add(new NumSetting("Скорость", "Скорость анимации.", 1.0, 0.2, 3.0, 0.1));

    public TargetEsp() {
        super("TargetESP", "Выделяет врага, которого ты ударил: свечение сквозь стены, круг 3D/призраки, уголки и вспышка при ударе.", Category.VISUAL);
        instance = this;
    }

    @Override
    public void onTick(Minecraft mc) {
        TargetManager.hitHoldMs = (long) (hold.get() * 1000);
    }

    @Override
    protected void onDisable() {
        TargetManager.hitHoldMs = 8000;
    }

    /** Текущая выделенная цель. */
    public LivingEntity current() {
        LivingEntity t = TargetManager.hit();
        if (t == null && when.is("Удар или прицел")) t = TargetManager.get();
        return t;
    }

    /** 0..1: насколько видно выделение (гаснет в конце времени удержания). */
    private float fade(LivingEntity t) {
        return TargetManager.isHitTarget(t) ? TargetManager.hitFade() : 1f;
    }

    private float flashAmount(LivingEntity t) {
        if (!flash.get() || !TargetManager.isHitTarget(t)) return 0f;
        long s = TargetManager.sinceHit();
        return s > 220 ? 0f : 1f - s / 220f;
    }

    private int baseColor(LivingEntity t, double offset) {
        int c = switch (colorMode.get()) {
            case "Радуга" -> Draw.rainbow(0.3 * speed.get(), offset);
            case "Свой" -> color.argb(offset);
            default -> Draw.healthColor(t.getHealth(), t.getMaxHealth());
        };
        float f = flashAmount(t);
        if (f > 0) c = mix(c, 0xFFFFFFFF, f);
        return c;
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = (int) (ar + (br - ar) * t), g = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ---------- свечение (через MinecraftMixin / EntityMixin) ----------

    public static boolean shouldGlow(Entity e) {
        TargetEsp m = instance;
        if (m == null || !m.isEnabled() || !m.glow.get()) return false;
        LivingEntity t = m.current();
        return t != null && t == e && !e.isInvisible();
    }

    /** Цвет контура (RGB) для цели или null. */
    public static Integer glowColor(Entity e) {
        if (!shouldGlow(e)) return null;
        TargetEsp m = instance;
        return m.baseColor((LivingEntity) e, 0) & 0xFFFFFF;
    }

    // ---------- 3D ----------

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        LivingEntity t = current();
        if (t == null || style.is("Нет") || style.is("Точки")) return;
        if (t == mc.getCameraEntity() && !mc.gameRenderer.getMainCamera().isDetached()) return;
        Vec3 pos = t.getPosition(pt);
        double w = t.getBbWidth(), h = t.getBbHeight();
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        float a = fade(t);
        if (a <= 0.01f) return;

        if (style.is("Круг 3D")) {
            double radius = w * 0.75 + 0.12;
            double phase = time * 1.8;
            int segs = 48;
            // цвета сегментов считаем один раз на кадр, а не для каждого из 9 кругов
            int[] cols = new int[segs + 1];
            for (int i = 1; i <= segs; i++) cols[i] = baseColor(t, (double) i / segs) & 0xFFFFFF;
            // мягкое кольцо под ногами - видно, где цель стоит, даже в толпе
            R3D.circle(pos.x, pos.y + 0.02, pos.z, radius, 40, Draw.withAlpha(cols[1], (int) (110 * a)), 2f);
            // основной круг + шлейф из 8 бледнеющих кругов
            for (int k = 0; k < 9; k++) {
                double p = phase - k * 0.07;
                double y = pos.y + h * (0.5 + 0.48 * Math.sin(p));
                int alpha = (int) ((k == 0 ? 255 : 150 - k * 15) * a);
                if (alpha <= 4) continue;
                float width = k == 0 ? 3.0f : 2.0f;
                double px = pos.x + radius, pz = pos.z;
                for (int i = 1; i <= segs; i++) {
                    double ang = Math.PI * 2 * i / segs;
                    double nx = pos.x + Math.cos(ang) * radius, nz = pos.z + Math.sin(ang) * radius;
                    int c = Draw.withAlpha(cols[i], alpha);
                    R3D.line(px, y, pz, nx, y, nz, c, width);
                    px = nx;
                    pz = nz;
                }
            }
        } else if (style.is("Призраки")) {
            double radius = w * 0.8 + 0.25;
            for (int g = 0; g < 3; g++) {
                int gc = baseColor(t, g / 3.0) & 0xFFFFFF;
                for (int k = 0; k < 14; k++) {
                    double ang = time * 2.6 + g * (Math.PI * 2 / 3) - k * 0.09;
                    double y = pos.y + h * 0.5 + Math.sin(time * 1.7 + g * 2.1 - k * 0.09) * h * 0.38;
                    double x = pos.x + Math.cos(ang) * radius, z = pos.z + Math.sin(ang) * radius;
                    double s = 0.11 * (1.0 - k / 16.0);
                    int alpha = (int) ((200 - k * 13) * a);
                    if (alpha <= 4) continue;
                    int c = Draw.withAlpha(gc, alpha);
                    R3D.boxFilled(new AABB(x - s, y - s, z - s, x + s, y + s, z + s), c);
                }
            }
        }
    }

    // ---------- HUD ----------

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        LivingEntity t = current();
        if (t == null) return;
        if (t == mc.getCameraEntity() && !mc.gameRenderer.getMainCamera().isDetached()) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        Vec3 pos = t.getPosition(pt);
        double w = t.getBbWidth(), h = t.getBbHeight();
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        float a = fade(t);
        if (a <= 0.01f) return;

        if (style.is("Точки")) {
            double[] o = new double[3];
            int n = 28;
            double radius = w * 0.75 + 0.15;
            for (int layer = 0; layer < 3; layer++) {
                double phase = time * 2.0 - layer * 0.35;
                double y = pos.y + h * (0.5 + 0.5 * Math.sin(phase));
                int alpha = (int) ((layer == 0 ? 255 : (layer == 1 ? 140 : 70)) * a);
                for (int i = 0; i < n; i++) {
                    double ang = (double) i / n * Math.PI * 2 + time * 1.5;
                    double x = pos.x + Math.cos(ang) * radius;
                    double z = pos.z + Math.sin(ang) * radius;
                    if (!Projection.project(x, y, z, o)) continue;
                    Draw.dot(g, o[0], o[1], layer == 0 ? 3 : 2, Draw.withAlpha(baseColor(t, (double) i / n) & 0xFFFFFF, alpha));
                }
            }
        }

        double hw = w / 2.0;
        double[] r = Projection.boxRect(new AABB(pos.x - hw, pos.y, pos.z - hw, pos.x + hw, pos.y + h, pos.z + hw));
        if (r == null) return;
        int x1 = (int) r[0] - 3, y1 = (int) r[1] - 3, x2 = (int) r[2] + 3, y2 = (int) r[3] + 3;
        if (corners.get()) {
            // уголки "сжимаются" к цели сразу после удара
            int pulse = (int) (flashAmount(t) * 4);
            x1 -= pulse; y1 -= pulse; x2 += pulse; y2 += pulse;
            int len = Math.max(4, Math.min(12, (x2 - x1) / 4));
            int c = Draw.withAlpha(baseColor(t, 0) & 0xFFFFFF, (int) (255 * a));
            int sh = Draw.withAlpha(0, (int) (160 * a));
            corner(g, x1, y1, len, 1, 1, sh, c);
            corner(g, x2, y1, len, -1, 1, sh, c);
            corner(g, x1, y2, len, 1, -1, sh, c);
            corner(g, x2, y2, len, -1, -1, sh, c);
        }
        if (label.get()) {
            String s = String.format("%.1f HP", t.getHealth() + t.getAbsorptionAmount());
            if (TargetManager.isHitTarget(t)) s += "  x" + TargetManager.hitCount();
            int tw = Draw.width(s);
            int cx = (x1 + x2) / 2;
            Draw.rect(g, cx - tw / 2 - 3, y1 - 14, cx + tw / 2 + 3, y1 - 3, Draw.withAlpha(0, (int) (150 * a)));
            Draw.textCentered(g, s, cx, y1 - 12, Draw.withAlpha(baseColor(t, 0) & 0xFFFFFF, (int) (255 * a)));
        }
    }

    /** Уголок с тенью: (x, y) - вершина, sx/sy - куда смотрят "усы". */
    private static void corner(GuiGraphics g, int x, int y, int len, int sx, int sy, int shadow, int c) {
        int hx2 = x + sx * len, vy2 = y + sy * len;
        Draw.rect(g, Math.min(x, hx2) - 1, Math.min(y, y + sy * 2) - 1, Math.max(x, hx2) + 1, Math.max(y, y + sy * 2) + 1, shadow);
        Draw.rect(g, Math.min(x, x + sx * 2) - 1, Math.min(y, vy2) - 1, Math.max(x, x + sx * 2) + 1, Math.max(y, vy2) + 1, shadow);
        Draw.rect(g, Math.min(x, hx2), Math.min(y, y + sy * 2), Math.max(x, hx2), Math.max(y, y + sy * 2), c);
        Draw.rect(g, Math.min(x, x + sx * 2), Math.min(y, vy2), Math.max(x, x + sx * 2), Math.max(y, vy2), c);
    }
}

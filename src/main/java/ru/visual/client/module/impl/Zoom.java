package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import ru.visual.client.VisualClient;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;

/**
 * Зум через подмену FOV при отрисовке (GameRendererMixin.getFov):
 * настройка FOV игры не трогается, поэтому после вылета/закрытия игры ничего не "залипает".
 */
public class Zoom extends Module implements Config.DefaultOn {
    private static Zoom instance;

    private final NumSetting strength = add(new NumSetting("Сила зума", "Во сколько раз приближать (3 = в три раза).", 3, 1.5, 10, 0.5));
    private final BoolSetting smooth = add(new BoolSetting("Плавность", "Плавное приближение и отдаление.", true));
    private final NumSetting speed = add(new NumSetting("Скорость", "Скорость плавного зума.", 0.35, 0.1, 1.0, 0.05));
    private final BoolSetting scroll = add(new BoolSetting("Колесо мыши", "Колесом мыши во время зума можно менять силу приближения.", true));
    private final BoolSetting cinematic = add(new BoolSetting("Плавная камера", "Включать кинематографичную (сглаженную) камеру во время зума.", false));

    private float factor = 1f, prevFactor = 1f;
    private double scrollMul = 1.0;
    private boolean zooming;
    private boolean savedSmoothCamera;

    public Zoom() {
        super("Zoom", "Приближение камеры, пока зажата клавиша (по умолчанию C). Колесо мыши - сила зума.", Category.VISUAL);
        instance = this;
    }

    @Override
    public boolean tickOutsideWorld() {
        return true;
    }

    @Override
    public void onTick(Minecraft mc) {
        boolean down = VisualClient.zoomKey != null && VisualClient.zoomKey.isDown() && mc.screen == null;
        if (down && !zooming) {
            zooming = true;
            scrollMul = 1.0;
            savedSmoothCamera = mc.options.smoothCamera;
            if (cinematic.get()) mc.options.smoothCamera = true;
        } else if (!down && zooming) {
            zooming = false;
            if (cinematic.get()) mc.options.smoothCamera = savedSmoothCamera;
        }
        float target = zooming ? (float) (strength.get() * scrollMul) : 1f;
        prevFactor = factor;
        if (smooth.get()) {
            factor += (target - factor) * speed.asFloat();
            if (Math.abs(target - factor) < 0.01f) factor = target;
        } else {
            factor = target;
        }
    }

    @Override
    protected void onDisable() {
        if (zooming && cinematic.get()) Minecraft.getInstance().options.smoothCamera = savedSmoothCamera;
        zooming = false;
        factor = prevFactor = 1f;
    }

    @Override
    public void onShutdown() {
        onDisable();
    }

    @Override
    public String info() {
        return zooming ? String.format(java.util.Locale.ROOT, "x%.1f", strength.get() * scrollMul) : null;
    }

    /** Вызывается из миксина: FOV с учётом зума. */
    public static float modifyFov(float fov) {
        Zoom z = instance;
        if (z == null || !z.isEnabled()) return fov;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float f = Mth.lerp(pt, z.prevFactor, z.factor);
        if (f <= 1.001f) return fov;
        return fov / f;
    }

    /** true - прокрутка использована для зума. */
    public static boolean onScroll(double dy) {
        Zoom z = instance;
        if (z == null || !z.isEnabled() || !z.zooming || !z.scroll.get() || dy == 0) return false;
        z.scrollMul = Mth.clamp(z.scrollMul * (dy > 0 ? 1.25 : 0.8), 0.25, 5.0);
        return true;
    }
}

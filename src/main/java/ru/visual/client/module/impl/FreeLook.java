package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import ru.visual.client.VisualClient;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

/**
 * Свободная камера: мышь крутит только камеру, персонаж не поворачивается и продолжает бежать в прежнюю сторону.
 * Работает через миксины EntityMixin (перехват поворота мышью) и CameraMixin (подмена углов камеры).
 * Если CameraMixin не применился в этой версии игры, перехват мыши отключается сам (чтобы мышь не "умерла").
 */
public class FreeLook extends Module implements Config.DefaultOn {
    private final ModeSetting trigger = add(new ModeSetting("Включение", "Когда включается свободная камера: во время бега или пока зажата клавиша (Left Alt по умолчанию).", "При беге",
            "При беге", "Пока зажата клавиша"));
    private final BoolSetting thirdPerson = add(new BoolSetting("Вид от 3 лица", "Автоматически переключать камеру на вид от третьего лица, пока свободная камера активна.", true));
    private final BoolSetting sync = add(new BoolSetting("Повернуть персонажа при выходе", "При выходе персонаж разворачивается туда, куда смотрит камера (иначе камера резко вернётся назад).", true));
    private final NumSetting release = add(new NumSetting("Задержка выхода (тики)", "Сколько тиков после остановки бега камера остаётся свободной, чтобы не дёргаться.", 10, 0, 40, 1));

    public static boolean active;
    private static float camYaw, camPitch;
    private static long lastCameraHook;

    private boolean ownsCameraType;
    private CameraType savedType;
    private int idle;

    public FreeLook() {
        super("FreeLook", "Свободная камера: крути камеру мышью, а персонаж продолжает бежать вперёд и не поворачивается.", Category.MOVEMENT);
    }

    // ---------- вызывается из миксинов ----------

    /** Угол рысканья камеры. */
    public static float cameraYaw(float incoming) {
        lastCameraHook = System.nanoTime();
        if (!active) return incoming;
        Minecraft mc = Minecraft.getInstance();
        boolean front = mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT;
        return camYaw + (front ? 180f : 0f);
    }

    public static float cameraPitch(float incoming) {
        if (!active) return incoming;
        Minecraft mc = Minecraft.getInstance();
        boolean front = mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT;
        return front ? -camPitch : camPitch;
    }

    /** true - поворот мышью перехвачен и должен быть отменён для персонажа. */
    public static boolean onTurn(double yRot, double xRot) {
        if (!active) return false;
        if (System.nanoTime() - lastCameraHook > 500_000_000L) return false; // камера не подключена - не перехватываем
        camYaw += (float) (yRot * 0.15);
        camPitch = Mth.clamp(camPitch + (float) (xRot * 0.15), -90f, 90f);
        return true;
    }

    // ---------- логика включения ----------

    private boolean wanted(Minecraft mc) {
        if (mc.screen != null) return false;
        if (trigger.is("При беге")) {
            return mc.player.isSprinting() || (mc.options.keyUp.isDown() && mc.options.keySprint.isDown());
        }
        return VisualClient.freeLookKey != null && VisualClient.freeLookKey.isDown();
    }

    @Override
    public boolean tickOutsideWorld() {
        return true;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) {
            active = false;
            return;
        }
        boolean want = wanted(mc);
        if (want) idle = 0;
        else if (active) idle++;

        if (want && !active) {
            if (System.nanoTime() - lastCameraHook > 2_000_000_000L) return; // камера-миксин не работает: не включаемся
            camYaw = mc.player.getYRot();
            camPitch = mc.player.getXRot();
            active = true;
            if (thirdPerson.get() && mc.options.getCameraType().isFirstPerson()) {
                savedType = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                ownsCameraType = true;
            }
        } else if (active && !want && idle > release.asInt()) {
            deactivate(mc);
        }
    }

    private void deactivate(Minecraft mc) {
        active = false;
        if (mc.player != null && sync.get()) {
            mc.player.setYRot(camYaw);
            mc.player.setXRot(camPitch);
        }
        if (ownsCameraType && savedType != null) mc.options.setCameraType(savedType);
        ownsCameraType = false;
        idle = 0;
    }

    @Override
    protected void onDisable() {
        if (active) deactivate(Minecraft.getInstance());
        active = false;
    }

    @Override
    public void onShutdown() {
        active = false;
    }

    @Override
    public String info() {
        return active ? Lang.t("активна") : null;
    }

    public static FreeLook get() {
        for (Module m : ModuleManager.MODULES) if (m instanceof FreeLook f) return f;
        return null;
    }
}

package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import ru.visual.client.VisualClient;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.NumSetting;

public class Zoom extends Module implements Config.DefaultOn {
    private final NumSetting fov = add(new NumSetting("FOV зума", "Угол обзора при зуме. Меньше = сильнее приближение.", 30, 30, 70, 1));
    private boolean zooming;
    private int saved;

    public Zoom() {
        super("Zoom", "Приближение камеры, пока зажата клавиша (по умолчанию C, меняется в настройках управления).", Category.VISUAL);
    }

    @Override
    public void onTick(Minecraft mc) {
        boolean down = VisualClient.zoomKey != null && VisualClient.zoomKey.isDown() && mc.screen == null;
        if (down && !zooming) {
            saved = mc.options.fov().get();
            zooming = true;
        }
        if (zooming) {
            if (down) {
                mc.options.fov().set(fov.asInt());
            } else {
                mc.options.fov().set(saved);
                zooming = false;
            }
        }
    }

    @Override
    protected void onDisable() {
        restore();
    }

    @Override
    public void onShutdown() {
        restore();
    }

    private void restore() {
        if (zooming) {
            Minecraft.getInstance().options.fov().set(saved);
            zooming = false;
        }
    }
}

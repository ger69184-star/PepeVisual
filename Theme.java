package ru.visual.client.util;

import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.NumSetting;

/** Глобальная тема интерфейса. Значения задаёт модуль Interface. */
public final class Theme {
    public static Module owner;
    public static ColorSetting accent;
    public static NumSetting opacity;
    public static BoolSetting rainbowBars;
    public static NumSetting rainbowSpeed;

    private Theme() {}

    public static boolean active() {
        return owner != null && owner.isEnabled() && accent != null;
    }
}

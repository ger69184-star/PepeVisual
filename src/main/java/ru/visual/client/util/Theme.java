package ru.visual.client.util;

import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

/** Глобальная тема интерфейса. Значения задаёт модуль Interface. */
public final class Theme {
    public static Module owner;
    public static ColorSetting accent;
    public static NumSetting opacity;
    public static BoolSetting rainbowBars;
    public static NumSetting rainbowSpeed;
    public static ModeSetting font;
    public static NumSetting radius;
    public static BoolSetting shadows;
    public static BoolSetting textShadow;

    private Theme() {}

    public static boolean active() {
        return owner != null && owner.isEnabled() && accent != null;
    }

    /** Гладкий шрифт (Inter) вместо пиксельного. Работает, даже если Interface выключен. */
    public static boolean smoothFont() {
        return font == null || !font.is("Minecraft");
    }

    public static boolean boldFont() {
        return font != null && font.is("Жирный");
    }

    public static int radius() {
        return radius == null ? 4 : radius.asInt();
    }

    public static boolean shadows() {
        return shadows == null || shadows.get();
    }

    public static boolean textShadow() {
        return textShadow == null || textShadow.get();
    }
}

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

    // ---- настройки меню (модуль Menu, вкладка Menu)
    public static ModeSetting menuTheme;
    public static NumSetting menuScale, menuOpacity, dim, animSpeed, menuWidth, menuHeight, menuRadius;
    public static BoolSetting blur, descriptions, clickSound;

    /** Названия тем меню (значения настройки "Тема меню"). */
    public static final String[] THEMES = {"Тёмная", "Полночь", "AMOLED", "Графит", "Светлая"};

    /**
     * Палитра окна меню. Порядок полей: окно, боковая панель, карточка, карточка под курсором, поле, поле под курсором,
     * текст, приглушённый, тусклый, текст вкладок, название выключенной функции, название включённой, выключенный переключатель,
     * выпадающий список/подсказка, текст на поле клавиши, цвет подсветки строк (RGB).
     */
    public record Palette(int window, int sidebar, int card, int cardHover, int field, int fieldHover,
                          int text, int muted, int dim, int tab, int nameOff, int nameOn, int switchOff,
                          int popup, int keyText, int overlay, boolean light) {}

    private static final Palette[] PALETTES = {
            new Palette(0xFF111118, 0x50000000, 0xFF1A1A24, 0xFF23232F, 0xFF2A2A38, 0xFF30303F,
                    0xFFE8E8F0, 0xFF8A8A9A, 0xFF6A6A78, 0xFFA4A4B4, 0xFFB8B8C8, 0xFFFFFFFF, 0xFF3A3A4A,
                    0xFA1C1C27, 0xFFD0D0E0, 0xFFFFFF, false),
            new Palette(0xFF0D1424, 0x40000814, 0xFF15203A, 0xFF1C2947, 0xFF24345A, 0xFF2B3D68,
                    0xFFE6EEFF, 0xFF8796B8, 0xFF63708F, 0xFFA0AECC, 0xFFB4C2E0, 0xFFFFFFFF, 0xFF34446A,
                    0xFA16223D, 0xFFCCD8F0, 0xFFFFFF, false),
            new Palette(0xFF000000, 0xFF0A0A0B, 0xFF0F0F11, 0xFF18181C, 0xFF202024, 0xFF2A2A30,
                    0xFFF0F0F0, 0xFF8C8C8C, 0xFF666666, 0xFFA8A8A8, 0xFFBDBDBD, 0xFFFFFFFF, 0xFF333338,
                    0xFA101012, 0xFFD6D6D6, 0xFFFFFF, false),
            new Palette(0xFF202226, 0x30000000, 0xFF2A2D33, 0xFF33373E, 0xFF3B3F47, 0xFF444952,
                    0xFFEDEFF2, 0xFF9AA0A8, 0xFF767C85, 0xFFB0B5BC, 0xFFC4C8CE, 0xFFFFFFFF, 0xFF4A4F57,
                    0xFA2A2D33, 0xFFD8DCE0, 0xFFFFFF, false),
            new Palette(0xFFF3F4F8, 0x12000000, 0xFFFFFFFF, 0xFFF0F1F6, 0xFFE4E6EE, 0xFFD9DCE6,
                    0xFF1C1D24, 0xFF6B6F7E, 0xFF9296A3, 0xFF4A4D5A, 0xFF3A3C48, 0xFF101118, 0xFFC6C9D4,
                    0xFAFFFFFF, 0xFF2A2C36, 0x000000, true),
    };

    private Theme() {}

    public static Palette palette() {
        int i = 0;
        if (menuTheme != null) for (int k = 0; k < THEMES.length; k++) if (menuTheme.is(THEMES[k])) i = k;
        return PALETTES[i];
    }

    public static float menuScale() {
        return menuScale == null ? 1f : menuScale.asFloat();
    }

    public static float animSpeed() {
        return animSpeed == null ? 1f : animSpeed.asFloat();
    }

    public static int menuAlpha() {
        return menuOpacity == null ? 0xF2 : (int) Math.round(menuOpacity.get() * 255);
    }

    public static float dim() {
        return dim == null ? 1f : dim.asFloat();
    }

    public static boolean blur() {
        return blur == null || blur.get();
    }

    public static boolean descriptions() {
        return descriptions == null || descriptions.get();
    }

    public static boolean clickSound() {
        return clickSound != null && clickSound.get();
    }

    public static int menuWidth() {
        return menuWidth == null ? 600 : menuWidth.asInt();
    }

    public static int menuHeight() {
        return menuHeight == null ? 370 : menuHeight.asInt();
    }

    public static int menuRadius() {
        return menuRadius == null ? 9 : menuRadius.asInt();
    }

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

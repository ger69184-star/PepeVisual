package ru.visual.client.setting;

import net.minecraft.util.Mth;
import ru.visual.client.util.Draw;

/** Цвет: оттенок + насыщенность, либо радужный режим. В меню: тяни мышью по полосе, ПКМ - радуга. */
public class ColorSetting extends Setting<Integer> {
    public float hue, sat, bri = 1f;
    public boolean rainbow;

    public ColorSetting(String name, String description, int rgb) {
        super(name, description, rgb);
        setRgb(rgb);
    }

    /** Задать цвет в RGB (без радуги). */
    public void setRgb(int rgb) {
        rainbow = false;
        // Свой перевод RGB -> HSV: java.awt.Color не используем (AWT вместе с GLFW может ломать запуск на macOS)
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        hue = h;
        sat = max == 0 ? 0 : d / max;
        bri = max;
    }

    /** Текущий цвет ARGB (раньше возвращалось значение из конструктора и не менялось). */
    @Override
    public Integer get() {
        return argb();
    }

    public int argb() {
        if (rainbow) return Draw.rainbow(0.2, 0);
        return 0xFF000000 | Mth.hsvToRgb(hue, sat, bri);
    }

    public int argb(double offset) {
        if (rainbow) return Draw.rainbow(0.2, offset);
        return argb();
    }
}

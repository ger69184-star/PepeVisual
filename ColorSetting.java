package ru.visual.client.setting;

import net.minecraft.util.Mth;
import ru.visual.client.util.Draw;

/** Цвет: оттенок + насыщенность, либо радужный режим. В меню: тяни мышью по полосе, ПКМ - радуга. */
public class ColorSetting extends Setting<Integer> {
    public float hue, sat, bri = 1f;
    public boolean rainbow;

    public ColorSetting(String name, String description, int rgb) {
        super(name, description, rgb);
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        hue = hsb[0];
        sat = hsb[1];
        bri = hsb[2];
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

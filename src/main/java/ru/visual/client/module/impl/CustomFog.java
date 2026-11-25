package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

/** Туман: убрать, отодвинуть/приблизить, перекрасить (FogRendererMixin). */
public class CustomFog extends Module {
    private static CustomFog instance;

    private final ModeSetting mode = add(new ModeSetting("Режим", "Убрать - тумана нет совсем; Дистанция - множитель дальности тумана.", "Убрать", "Убрать", "Дистанция"));
    private final NumSetting distance = add(new NumSetting("Множитель дальности", "Для режима Дистанция: <1 - туман ближе (атмосферно), >1 - дальше.", 1.5, 0.2, 4.0, 0.1));
    private final BoolSetting liquids = add(new BoolSetting("В воде и лаве", "Менять туман и под водой/в лаве.", true));
    private final BoolSetting recolor = add(new BoolSetting("Свой цвет", "Перекрасить туман (и горизонт) в свой цвет.", false));
    private final ColorSetting color = add(new ColorSetting("Цвет тумана", "Цвет тумана при включённом \"Свой цвет\".", 0xB070FF));

    public CustomFog() {
        super("Custom Fog", "Убирает туман или меняет его дальность и цвет (как Fog в FlyVisual).", Category.VISUAL);
        instance = this;
    }

    private static boolean applies() {
        CustomFog m = instance;
        if (m == null || !m.isEnabled()) return false;
        if (m.liquids.get()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return false;
        FogType t = mc.gameRenderer.getMainCamera().getFluidInCamera();
        return t == FogType.NONE || t == FogType.ATMOSPHERIC;
    }

    private static float apply(float v) {
        if (v == Float.MAX_VALUE || !applies()) return v;
        if (instance.mode.is("Убрать")) return 1.0e6f;
        return v * instance.distance.asFloat();
    }

    public static float start(float v) {
        return apply(v);
    }

    public static float end(float v) {
        return apply(v);
    }

    public static void color(Vector4f c) {
        CustomFog m = instance;
        if (m == null || !m.isEnabled() || !m.recolor.get() || c.w == 0f && c.x == 0f && c.y == 0f && c.z == 0f) return;
        int rgb = m.color.argb();
        c.set(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, c.w);
    }
}

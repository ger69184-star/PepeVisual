package ru.visual.client.module.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

/** Анимации удара/взмаха предметом от первого лица. Применяются через миксин ItemInHandRendererMixin. */
public class HitAnimations extends Module implements Config.DefaultOn {
    private final ModeSetting mode = add(new ModeSetting("Анимация", "Вид взмаха рукой при ударе.", "Взмах",
            "Ванильная", "Мягкий", "Резкий", "Толчок", "Взмах", "Вращение", "Постукивание"));
    private final NumSetting power = add(new NumSetting("Сила", "Амплитуда анимации: меньше - спокойнее, больше - размашистее.", 1.0, 0.3, 2.5, 0.1));

    public HitAnimations() {
        super("HitAnimations", "Другие анимации удара рукой и предметом от первого лица (только визуально).", Category.VISUAL);
    }

    @Override
    public String info() {
        return mode.get();
    }

    /** true - если анимация применена и ванильную надо отменить. */
    public static boolean apply(PoseStack ps, HumanoidArm arm, float s) {
        HitAnimations m = ModuleManager.hitAnimations;
        if (m == null || !m.isEnabled() || m.mode.is("Ванильная")) return false;
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        float k = m.power.asFloat();
        float pi = (float) Math.PI;
        float f4 = Mth.sin(Mth.sqrt(s) * pi);
        float f3 = Mth.sin(s * s * pi);
        float fs = Mth.sin(s * pi);

        switch (m.mode.get()) {
            case "Мягкий", "Резкий" -> {
                float q = k * (m.mode.is("Мягкий") ? 0.6f : 1.4f);
                ps.translate(i * (-0.4f * q * f4), 0.2f * q * Mth.sin(Mth.sqrt(s) * pi * 2f), -0.2f * q * fs);
                ps.mulPose(Axis.YP.rotationDegrees(i * (45f + f3 * -20f * q)));
                ps.mulPose(Axis.ZP.rotationDegrees(i * f4 * -20f * q));
                ps.mulPose(Axis.XP.rotationDegrees(f4 * -80f * q));
                ps.mulPose(Axis.YP.rotationDegrees(i * -45f));
            }
            case "Толчок" -> ps.translate(0.0f, 0.05f * k * f4, -0.9f * k * f4);
            case "Взмах" -> {
                ps.translate(i * -0.25f * k * f4, -0.05f * k * f4, -0.35f * k * fs);
                ps.mulPose(Axis.ZP.rotationDegrees(i * -55f * k * f4));
                ps.mulPose(Axis.XP.rotationDegrees(-95f * k * f4));
            }
            case "Вращение" -> {
                ps.translate(i * -0.15f * k * f4, 0.0f, -0.3f * k * fs);
                ps.mulPose(Axis.YP.rotationDegrees(360f * s * i));
                ps.mulPose(Axis.XP.rotationDegrees(-40f * k * f4));
            }
            case "Постукивание" -> {
                ps.translate(0.0f, -0.12f * k * Mth.sin(s * pi * 3f), -0.2f * k * fs);
                ps.mulPose(Axis.XP.rotationDegrees(-25f * k * fs));
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}

package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

/** Растянутое разрешение: мир рисуется как при другом соотношении сторон (4:3 stretched и т.п.). */
public class AspectRatio extends Module {
    private static AspectRatio instance;

    private final ModeSetting mode = add(new ModeSetting("Соотношение", "Какое соотношение сторон имитировать.", "4:3", "4:3", "5:4", "16:10", "1:1", "Своё"));
    private final NumSetting custom = add(new NumSetting("Своё (ширина/высота)", "Для режима \"Своё\": 1.33 = 4:3, 1.78 = 16:9.", 1.33, 0.5, 2.5, 0.01));

    public AspectRatio() {
        super("Aspect Ratio", "Растягивает картинку мира как при другом соотношении сторон (4:3, 5:4, 16:10...). HUD не меняется.", Category.VISUAL);
        instance = this;
    }

    private double target() {
        return switch (mode.get()) {
            case "4:3" -> 4.0 / 3.0;
            case "5:4" -> 5.0 / 4.0;
            case "16:10" -> 16.0 / 10.0;
            case "1:1" -> 1.0;
            default -> custom.get();
        };
    }

    /** Вызывается из GameRendererMixin: правим матрицу проекции. */
    public static void apply(Matrix4f m) {
        AspectRatio a = instance;
        if (a == null || !a.isEnabled()) return;
        var win = Minecraft.getInstance().getWindow();
        if (win.getHeight() <= 0) return;
        double real = (double) win.getWidth() / win.getHeight();
        double t = a.target();
        if (t <= 0) return;
        m.scaleLocal((float) (real / t), 1f, 1f);
    }
}

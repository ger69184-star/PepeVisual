package ru.visual.client.module.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.NumSetting;

/** Положение и размер рук/предметов от первого лица (HandOffset). */
public class ViewModel extends Module {
    private static ViewModel instance;

    private final NumSetting x = add(new NumSetting("Сдвиг X", "Влево/вправо.", 0, -1, 1, 0.05));
    private final NumSetting y = add(new NumSetting("Сдвиг Y", "Вниз/вверх.", 0, -1, 1, 0.05));
    private final NumSetting z = add(new NumSetting("Сдвиг Z", "Ближе/дальше от камеры.", 0, -1, 1, 0.05));
    private final NumSetting scale = add(new NumSetting("Размер", "Масштаб рук и предметов.", 1.0, 0.3, 1.5, 0.05));

    public ViewModel() {
        super("ViewModel", "Сдвиг и размер рук и предметов от первого лица (Hand Offset).", Category.VISUAL);
        instance = this;
    }

    /** Вызывается из ItemInHandRendererMixin перед отрисовкой рук (стек уже сохранён вызывающим кодом). */
    public static void apply(PoseStack pose) {
        ViewModel m = instance;
        if (m == null || !m.isEnabled()) return;
        pose.translate(m.x.asFloat(), m.y.asFloat(), -m.z.asFloat());
        float s = m.scale.asFloat();
        if (s != 1f) pose.scale(s, s, s);
    }
}

package ru.visual.client.module.impl;

import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.NumSetting;

/**
 * Полная яркость через подмену gamma в карте освещения (LightTextureMixin).
 * Раньше выдавался клиентский эффект Ночного зрения: он мигал, конфликтовал с настоящим зельем
 * и снимал его при выключении модуля.
 */
public class Fullbright extends Module {
    private static Fullbright instance;

    private final NumSetting brightness = add(new NumSetting("Яркость", "Сила осветления (1 = ванильный максимум, 10 = как днём везде).", 10, 1, 15, 0.5));

    public Fullbright() {
        super("Fullbright", "Полная яркость: видно в пещерах и ночью. Не трогает эффекты и настройки игры.", Category.VISUAL);
        instance = this;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    public static Double gamma() {
        return instance.brightness.get();
    }
}

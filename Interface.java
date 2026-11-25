package ru.visual.client.module.impl;

import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Theme;

/** Кастомизация интерфейса клиента: цвета и прозрачность меню и HUD. */
public class Interface extends Module implements Config.DefaultOn {
    public final ColorSetting accent = add(new ColorSetting("Цвет темы", "Основной цвет меню и HUD. Тяни по полосе, ПКМ - радужный режим.", 0x8A63FF));
    public final NumSetting opacity = add(new NumSetting("Прозрачность фона", "Непрозрачность тёмных плашек HUD (1 = сплошной фон).", 0.75, 0.1, 1.0, 0.05));
    public final BoolSetting rainbowBars = add(new BoolSetting("Радужные полоски", "Акцентные полоски (список модулей, Watermark) переливаются.", false));
    public final NumSetting rainbowSpeed = add(new NumSetting("Скорость радуги", "Скорость переливания цветов.", 0.15, 0.05, 1.0, 0.05));

    public Interface() {
        super("Interface", "Кастомизация интерфейса клиента: цвет темы, прозрачность и радужные элементы.", Category.HUD);
        Theme.owner = this;
        Theme.accent = accent;
        Theme.opacity = opacity;
        Theme.rainbowBars = rainbowBars;
        Theme.rainbowSpeed = rainbowSpeed;
    }
}

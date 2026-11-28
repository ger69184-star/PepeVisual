package ru.visual.client.module.impl;

import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Theme;

/** Кастомизация интерфейса клиента: цвета и прозрачность меню и HUD. */
public class Interface extends Module implements Config.DefaultOn {
    public final ColorSetting accent = add(new ColorSetting("Цвет темы", "Основной цвет меню и HUD. Тяни по полосе, ПКМ - радужный режим.", 0x8A63FF));
    public final NumSetting opacity = add(new NumSetting("Прозрачность фона", "Непрозрачность тёмных плашек HUD (1 = сплошной фон).", 0.75, 0.1, 1.0, 0.05));
    public final BoolSetting rainbowBars = add(new BoolSetting("Радужные полоски", "Акцентные полоски (список модулей, Watermark) переливаются.", false));
    public final NumSetting rainbowSpeed = add(new NumSetting("Скорость радуги", "Скорость переливания цветов.", 0.15, 0.05, 1.0, 0.05));

    public final ModeSetting font = add(new ModeSetting("Шрифт", "Шрифт меню и HUD. Гладкий - Inter (сглаженный, поддерживает русский), Minecraft - стандартный пиксельный.",
            "Гладкий", "Гладкий", "Жирный", "Minecraft"));
    public final NumSetting radius = add(new NumSetting("Скругление углов", "Радиус скругления плашек HUD и меню (0 - квадратные).", 4, 0, 8, 1));
    public final BoolSetting shadows = add(new BoolSetting("Мягкие тени", "Мягкая тень под плашками HUD и окном меню.", true));
    public final BoolSetting textShadow = add(new BoolSetting("Тень текста в HUD", "Тень под текстом HUD - лучше читается на светлом фоне.", true));

    public Interface() {
        super("Interface", "Кастомизация интерфейса клиента: цвет темы, шрифт, скругление, тени, прозрачность и радужные элементы.", Category.MENU);
        Theme.owner = this;
        Theme.accent = accent;
        Theme.opacity = opacity;
        Theme.rainbowBars = rainbowBars;
        Theme.rainbowSpeed = rainbowSpeed;
        Theme.font = font;
        Theme.radius = radius;
        Theme.shadows = shadows;
        Theme.textShadow = textShadow;
    }
}

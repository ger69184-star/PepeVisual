package ru.visual.client.module.impl;

import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Lang;
import ru.visual.client.util.Theme;

/**
 * Настройки самого меню (вкладка Menu): язык, масштаб, тема окна, прозрачность, фон и анимации.
 * Карточка без переключателя - всегда включена. Цвет акцента и шрифт - в соседней карточке Interface.
 */
public class MenuSettings extends Module {
    public final ModeSetting language = add(new ModeSetting("Язык / Language", "Язык меню, подсказок и HUD. Language of the menu, tooltips and HUD.",
            "Русский", "Русский", "English"));
    public final NumSetting scale = add(new NumSetting("Масштаб меню", "Размер окна меню. Также кнопки - и + в шапке или Ctrl + колесо мыши.", 1.0, 0.6, 1.6, 0.05));
    public final ModeSetting theme = add(new ModeSetting("Тема меню", "Цвета окна: тёмная, синяя \"Полночь\", чёрная AMOLED, серый \"Графит\" или светлая.",
            "Тёмная", Theme.THEMES));
    public final NumSetting opacity = add(new NumSetting("Непрозрачность окна", "1 - сплошное окно, меньше - сквозь окно виден мир.", 0.95, 0.6, 1.0, 0.05));
    public final NumSetting width = add(new NumSetting("Ширина окна", "Максимальная ширина меню (на маленьком экране окно всё равно влезет).", 600, 460, 900, 20));
    public final NumSetting height = add(new NumSetting("Высота окна", "Максимальная высота меню.", 370, 300, 560, 10));
    public final NumSetting radius = add(new NumSetting("Скругление окна", "Радиус углов окна меню и карточек.", 9, 0, 14, 1));
    public final NumSetting dim = add(new NumSetting("Затемнение фона", "Насколько затемняется мир за меню (0 - не затемнять).", 1.0, 0.0, 1.5, 0.1));
    public final BoolSetting blur = add(new BoolSetting("Размытие фона", "Размывать мир за меню.", true));
    public final NumSetting anim = add(new NumSetting("Скорость анимаций", "Скорость плавных анимаций меню: 0.5 - медленно и мягко, 2 - почти мгновенно.", 1.0, 0.5, 2.0, 0.1));
    public final BoolSetting descriptions = add(new BoolSetting("Описания в карточках", "Показывать строку описания под названием функции (выкл - карточки компактнее).", true));
    public final BoolSetting clickSound = add(new BoolSetting("Звук кликов", "Тихий щелчок при включении функций в меню.", false));

    public MenuSettings() {
        super("Menu", "Настройки меню: язык, масштаб, тема окна, прозрачность, размытие и анимации. Цвет и шрифт - в Interface.", Category.MENU);
        Lang.language = language;
        Theme.menuTheme = theme;
        Theme.menuScale = scale;
        Theme.menuOpacity = opacity;
        Theme.menuWidth = width;
        Theme.menuHeight = height;
        Theme.menuRadius = radius;
        Theme.dim = dim;
        Theme.blur = blur;
        Theme.animSpeed = anim;
        Theme.descriptions = descriptions;
        Theme.clickSound = clickSound;
    }

    @Override
    public boolean settingsOnly() {
        return true;
    }
}

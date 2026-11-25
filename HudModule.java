package ru.visual.client.module;

import ru.visual.client.setting.NumSetting;

/** База для HUD-элементов: у каждого можно сдвинуть положение на экране. */
public abstract class HudModule extends Module {
    protected final NumSetting offX = add(new NumSetting("Смещение X", "Сдвиг элемента по горизонтали в пикселях (минус - влево).", 0, -500, 500, 1));
    protected final NumSetting offY = add(new NumSetting("Смещение Y", "Сдвиг элемента по вертикали в пикселях (минус - вверх).", 0, -300, 300, 1));

    protected HudModule(String name, String description) {
        super(name, description, Category.HUD);
    }

    protected int ox() {
        return offX.asInt();
    }

    protected int oy() {
        return offY.asInt();
    }
}

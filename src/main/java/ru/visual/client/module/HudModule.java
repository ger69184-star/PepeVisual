package ru.visual.client.module;

import ru.visual.client.setting.NumSetting;

/**
 * База для HUD-элементов: у каждого можно сдвинуть положение на экране
 * (ползунками в меню или мышью в редакторе HUD).
 */
public abstract class HudModule extends Module {
    protected final NumSetting offX = add(new NumSetting("Смещение X", "Сдвиг элемента по горизонтали в пикселях (минус - влево). Удобнее двигать мышью в редакторе HUD.", 0, -1000, 1000, 1));
    protected final NumSetting offY = add(new NumSetting("Смещение Y", "Сдвиг элемента по вертикали в пикселях (минус - вверх). Удобнее двигать мышью в редакторе HUD.", 0, -1000, 1000, 1));

    /** Прямоугольник, который элемент занял в последнем кадре (для редактора HUD). */
    private int bx1, by1, bx2, by2;
    private long boundsFrame = -1;

    protected HudModule(String name, String description) {
        super(name, description, Category.HUD);
    }

    protected int ox() {
        return offX.asInt();
    }

    protected int oy() {
        return offY.asInt();
    }

    /** Запомнить занятую область (вызывай из onHud). Несколько вызовов за кадр объединяются. */
    protected void bounds(int x1, int y1, int x2, int y2) {
        long frame = ModuleManager.frame();
        if (boundsFrame != frame) {
            boundsFrame = frame;
            bx1 = x1;
            by1 = y1;
            bx2 = x2;
            by2 = y2;
        } else {
            bx1 = Math.min(bx1, x1);
            by1 = Math.min(by1, y1);
            bx2 = Math.max(bx2, x2);
            by2 = Math.max(by2, y2);
        }
    }

    /** {x1, y1, x2, y2} или null, если элемент ничего не нарисовал недавно. */
    public int[] lastBounds() {
        if (boundsFrame < 0 || ModuleManager.frame() - boundsFrame > 5) return null;
        return new int[]{bx1, by1, bx2, by2};
    }

    /** Сдвинуть элемент на dx/dy пикселей (из редактора HUD). */
    public void moveBy(int dx, int dy) {
        offX.set(offX.get() + dx);
        offY.set(offY.get() + dy);
    }

    public void resetPosition() {
        offX.set(0);
        offY.set(0);
    }
}

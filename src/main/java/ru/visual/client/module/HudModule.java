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

    /**
     * Автораскладка: несдвинутые элементы у левого/правого края встают друг под другом и не налезают
     * (раньше InventoryHud, Keystrokes и SessionStats рисовались один поверх другого, а длинный ModuleList закрывал ItemCounter).
     * [1] - левая колонка, [2] - правая: нижний край последнего элемента в этом кадре.
     */
    private static final int[] FLOW = new int[3];
    private int flowCol;
    /** На сколько автораскладка опустила элемент (чтобы при перетаскивании он не прыгал). */
    private int flowShift;

    protected HudModule(String name, String description) {
        super(name, description, Category.HUD);
        // позиция двигается мышью в редакторе HUD - ползунки -1000..1000 в меню только мешали
        offX.hidden = true;
        offY.hidden = true;
    }

    protected int ox() {
        return offX.asInt();
    }

    protected int oy() {
        return offY.asInt();
    }

    /** Y для элемента в левой колонке (defY - место по умолчанию). */
    protected int left(int defY) {
        return place(1, defY);
    }

    /** Y для элемента в правой колонке. */
    protected int right(int defY) {
        return place(2, defY);
    }

    private int place(int col, int defY) {
        if (offX.asInt() != 0 || offY.asInt() != 0) {
            flowShift = 0;
            return defY + oy(); // игрок сам поставил элемент - не трогаем
        }
        flowCol = col;
        int y = Math.max(defY, FLOW[col]);
        flowShift = y - defY;
        return y;
    }

    /** Начало кадра HUD (ModuleManager.renderHud). */
    public static void beginFrame() {
        FLOW[1] = 0;
        FLOW[2] = 0;
    }

    /** После onHud: занятое место уходит в колонку, следующий элемент встанет ниже. */
    public void afterHud() {
        if (flowCol != 0 && boundsFrame == ModuleManager.frame()) FLOW[flowCol] = Math.max(FLOW[flowCol], by2 + 3);
        flowCol = 0;
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

    /** Вызывается из Draw автоматически. */
    public void track(int x1, int y1, int x2, int y2) {
        bounds(x1, y1, x2, y2);
    }

    /** {x1, y1, x2, y2} или null, если элемент ничего не нарисовал недавно. */
    public int[] lastBounds() {
        if (boundsFrame < 0 || ModuleManager.frame() - boundsFrame > 5) return null;
        return new int[]{bx1, by1, bx2, by2};
    }

    /** Сдвинуть элемент на dx/dy пикселей (из редактора HUD). */
    public void moveBy(int dx, int dy) {
        if (offX.asInt() == 0 && offY.asInt() == 0 && flowShift != 0) {
            offY.set((double) flowShift); // элемент стоял ниже из-за автораскладки - начинаем двигать с того же места
            flowShift = 0;
        }
        offX.set(offX.get() + dx);
        offY.set(offY.get() + dy);
    }

    public void resetPosition() {
        offX.set(0);
        offY.set(0);
    }
}

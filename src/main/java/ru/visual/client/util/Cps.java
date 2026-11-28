package ru.visual.client.util;

import java.util.ArrayDeque;

/** Счётчик кликов в секунду. Клики ловятся прямо из обработчика мыши (MouseHandlerMixin), а не раз в тик. */
public final class Cps {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();

    private Cps() {}

    public static synchronized void click(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) {
            LEFT.addLast(now);
            count(LEFT); // старые клики выбрасываем сразу: если HUD с CPS выключен, очередь иначе росла бы всю игру
        } else if (button == 1) {
            RIGHT.addLast(now);
            count(RIGHT);
        }
    }

    private static int count(ArrayDeque<Long> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    public static synchronized int left() {
        return count(LEFT);
    }

    public static synchronized int right() {
        return count(RIGHT);
    }
}

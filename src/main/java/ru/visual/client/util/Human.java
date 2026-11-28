package ru.visual.client.util;

import java.util.Random;

/** Случайные значения с «человеческим» разбросом (использует Aim для плавного поворота камеры). */
public final class Human {
    private static final Random R = new Random();

    private Human() {}

    /** Случайное число в интервале [a, b). */
    public static double rnd(double a, double b) {
        return a + R.nextDouble() * (b - a);
    }
}

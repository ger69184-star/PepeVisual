package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Random;

/**
 * "Человеческие" паузы для помощников. Человек не кликает с одинаковым интервалом:
 * паузы немного разные (нормальное распределение), иногда он задерживается подольше,
 * а после серии действий делает короткий перерыв. Плюс счётчик "игрок стоит спокойно":
 * перекладывать вещи в инвентаре без открытого окна можно только тогда, когда игрок не бежит и не крутит камерой.
 */
public final class Human {
    private static final Random R = new Random();
    /** Сколько тиков подряд игрок стоит на месте, не жмёт WASD, не двигает мышь и ничего не использует. */
    private static int still;

    private Human() {}

    public static double rnd(double a, double b) {
        return a + R.nextDouble() * (b - a);
    }

    public static int range(int min, int max) {
        return max <= min ? min : min + R.nextInt(max - min + 1);
    }

    public static boolean chance(double p) {
        return R.nextDouble() < p;
    }

    /** Пауза в тиках около base (±25%), иногда (7%) заметно дольше - "отвлёкся". */
    public static int ticks(double base) {
        double v = base * (1 + R.nextGaussian() * 0.25);
        if (R.nextDouble() < 0.07) v += base * rnd(0.8, 2.5) + 2;
        return Math.max(1, (int) Math.round(v));
    }

    /** Интервал между кликами в миллисекундах для заданной скорости (кликов в секунду). */
    public static long clickMs(double cps) {
        double base = 1000.0 / Math.max(1, cps);
        double v = base * (1 + R.nextGaussian() * 0.22);
        if (R.nextDouble() < 0.05) v += rnd(120, 380);
        return Math.max(45, Math.round(v));
    }

    /** Вызывается каждый тик. */
    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            still = 0;
            return;
        }
        var o = mc.options;
        boolean input = Keys.physical(mc, o.keyUp) || Keys.physical(mc, o.keyDown) || Keys.physical(mc, o.keyLeft)
                || Keys.physical(mc, o.keyRight) || Keys.physical(mc, o.keyJump) || o.keyUp.isDown() || o.keyJump.isDown();
        double hv = p.getDeltaMovement().horizontalDistanceSqr();
        boolean calm = !input && hv < 0.0025 && !p.isSprinting() && !p.isUsingItem() && !Aim.userActive() && !Aim.active()
                && (mc.screen == null || mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen);
        still = calm ? still + 1 : 0;
    }

    /** Игрок спокойно стоит хотя бы столько тиков (можно незаметно переложить вещь в инвентаре). */
    public static boolean standingStill(int ticks) {
        return still >= ticks;
    }

    /**
     * Темп работы помощника: паузы между действиями разные, после 10-25 действий - перерыв 0.6-2 сек.
     * {@link #ready()} вызывать один раз за тик.
     */
    public static final class Pacer {
        private int wait, actions, nextBreak = range(10, 25);

        public boolean ready() {
            if (wait > 0) {
                wait--;
                return false;
            }
            return true;
        }

        /** Действие сделано: следующая пауза около baseTicks. */
        public void done(double baseTicks) {
            done(baseTicks, true);
        }

        /** То же, breaks = делать ли перерывы после серии действий. */
        public void done(double baseTicks, boolean breaks) {
            wait = ticks(baseTicks);
            if (breaks && ++actions >= nextBreak) {
                actions = 0;
                nextBreak = range(10, 25);
                wait += range(12, 40);
            }
        }

        /** Подождать не меньше t тиков. */
        public void hold(int t) {
            wait = Math.max(wait, t);
        }

        public void reset() {
            wait = 0;
        }
    }

    /**
     * Настройки поведения помощника: "Базовый" (как раньше - быстро и точно) или "Продвинутый" (как живой игрок)
     * и подстройка продвинутого режима. Создаётся через Module.humanTune(...) первой строкой модуля.
     */
    public static final class Tune {
        public static final String BASIC = "Базовый", ADVANCED = "Продвинутый";
        public ru.visual.client.setting.ModeSetting mode;
        public ru.visual.client.setting.NumSetting reaction, camera;
        public ru.visual.client.setting.BoolSetting breaks, yieldMouse;

        public boolean advanced() {
            return mode == null || mode.is(ADVANCED);
        }

        /** Случайная пауза реакции min..max тиков с учётом настройки "Реакция". */
        public int react(int min, int max) {
            double k = reaction == null ? 1.0 : reaction.get();
            return range((int) Math.round(min * k), (int) Math.round(max * k));
        }

        public double cameraSpeed() {
            return camera == null ? 1.0 : camera.get();
        }

        public boolean breaks() {
            return breaks == null || breaks.get();
        }

        /** Уступать камеру, когда игрок сам двигает мышь. */
        public boolean yieldToMouse() {
            return yieldMouse == null || yieldMouse.get();
        }
    }
}

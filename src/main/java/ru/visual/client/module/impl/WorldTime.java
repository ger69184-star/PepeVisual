package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

/** Время суток только на твоём экране (ClientLevelDataMixin). */
public class WorldTime extends Module {
    private static WorldTime instance;

    private final ModeSetting mode = add(new ModeSetting("Режим", "Фиксированное - стоит на выбранном часе; Цикл - время идёт с заданной скоростью.", "Фиксированное", "Фиксированное", "Цикл"));
    private final NumSetting hour = add(new NumSetting("Час", "Час суток (0-23): 6 = рассвет, 12 = полдень, 18 = закат, 0 = полночь.", 12, 0, 23.5, 0.5));
    private final NumSetting speed = add(new NumSetting("Скорость цикла", "Во сколько раз быстрее обычного идёт время в режиме Цикл.", 20, 1, 200, 1));

    private double cycleTime = -1;

    public WorldTime() {
        super("World Time", "Своё время суток: вечный день, закат или ночь (видно только тебе).", Category.VISUAL);
        instance = this;
    }

    @Override
    protected void onEnable() {
        cycleTime = -1;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (!mode.is("Цикл")) return;
        if (cycleTime < 0) cycleTime = hourToTicks(hour.get());
        cycleTime = (cycleTime + speed.get()) % 24000.0;
    }

    private static double hourToTicks(double h) {
        // В Minecraft 0 тиков = 6:00 утра
        return ((h - 6.0) * 1000.0 % 24000.0 + 24000.0) % 24000.0;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    public static long dayTime() {
        WorldTime m = instance;
        if (m.mode.is("Цикл") && m.cycleTime >= 0) return (long) m.cycleTime;
        return (long) hourToTicks(m.hour.get());
    }

    @Override
    public String info() {
        return mode.is("Цикл") ? "цикл" : String.format(java.util.Locale.ROOT, "%02d:%02d", (int) hour.get().doubleValue(), (int) Math.round((hour.get() % 1) * 60));
    }
}

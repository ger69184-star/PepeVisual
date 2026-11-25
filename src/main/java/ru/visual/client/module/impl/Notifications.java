package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.module.Module;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;

import java.util.ArrayList;
import java.util.List;

/** Всплывающие уведомления в правом нижнем углу (включение/выключение функций и др.). */
public class Notifications extends HudModule implements Config.DefaultOn {
    private static Notifications instance;

    private record Note(String text, boolean on, long time) {}

    private final NumSetting duration = add(new NumSetting("Длительность (сек)", "Сколько секунд показывается уведомление.", 2.0, 0.5, 6.0, 0.5));
    private final List<Note> notes = new ArrayList<>();

    public Notifications() {
        super("Notifications", "Всплывающие уведомления о включении и выключении функций.");
        instance = this;
    }

    /** Вызывается из ModuleManager при переключении модуля. */
    public static void onToggle(Module m) {
        Notifications n = instance;
        if (n == null || !n.isEnabled() || m == n) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        synchronized (n.notes) {
            n.notes.add(new Note(m.name, m.isEnabled(), System.currentTimeMillis()));
            while (n.notes.size() > 6) n.notes.remove(0);
        }
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        long now = System.currentTimeMillis();
        long life = (long) (duration.get() * 1000);
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        // Над списком эффектов (Potion HUD тоже в правом нижнем углу)
        int y = h - 28 - mc.player.getActiveEffects().size() * 12 + oy();
        synchronized (notes) {
            notes.removeIf(n -> now - n.time() > life + 300);
            for (int i = notes.size() - 1; i >= 0; i--) {
                Note n = notes.get(i);
                long age = now - n.time();
                // Выезд справа (200 мс) и уезд обратно в конце
                double slide = age < 200 ? 1 - age / 200.0 : age > life ? Math.min(1, (age - life) / 300.0) : 0;
                String text = n.text() + (n.on() ? " §aвключён" : " §cвыключен");
                int tw = Draw.width(text) + 14;
                int x2 = w - 4 + ox() + (int) (slide * (tw + 8));
                int x1 = x2 - tw;
                Draw.rect(g, x1, y, x2, y + 18, Draw.bg());
                Draw.rect(g, x1, y, x1 + 2, y + 18, n.on() ? 0xFF55FF77 : 0xFFFF5555);
                // полоска оставшегося времени
                double left = Math.max(0, 1 - (double) age / life);
                Draw.rect(g, x1 + 2, y + 17, x1 + 2 + (int) ((tw - 2) * left), y + 18, Draw.accent());
                Draw.text(g, text, x1 + 8, y + 5, Draw.WHITE);
                y -= 21;
            }
        }
    }
}

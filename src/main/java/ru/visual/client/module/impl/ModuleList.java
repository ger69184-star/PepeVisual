package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.module.Module;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.util.Draw;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ModuleList extends HudModule implements Config.DefaultOn {
    public ModuleList() {
        super("ModuleList", "Список включённых функций справа вверху экрана.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        List<Module> on = new ArrayList<>();
        for (Module m : ModuleManager.MODULES) {
            // HUD-элементы и кнопки оптимизации в списке только мешают
            if (m.isEnabled() && m.category != Category.HUD && m.category != Category.OPTIMIZATION) on.add(m);
        }
        on.sort(Comparator.comparingInt((Module m) -> -Draw.width(label(m))));
        int w = mc.getWindow().getGuiScaledWidth() + ox();
        int y = right(4), i = 0;
        for (Module m : on) {
            String s = label(m);
            int tw = Draw.width(s);
            int c = Draw.bar(i);
            Draw.panel(g, w - tw - 8, y, w - 2, y + 11, Draw.bg(), false);
            Draw.rect(g, w - 4, y + 2, w - 3, y + 9, c);
            Draw.text(g, m.name, w - tw - 5, y + 2, c);
            if (m.info() != null) Draw.text(g, " " + m.info(), w - tw - 5 + Draw.width(m.name), y + 2, Draw.GRAY);
            y += 11;
            i++;
        }
    }

    private static String label(Module m) {
        return m.info() == null ? m.name : m.name + " " + m.info();
    }
}

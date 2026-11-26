package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class NearbyPlayers extends HudModule implements Config.DefaultOn {
    private final NumSetting max = add(new NumSetting("Максимум строк", "Сколько ближайших игроков показывать в списке.", 6, 1, 12, 1));
    private final NumSetting range = add(new NumSetting("Дальность", "Игроки дальше этого расстояния не показываются (блоки).", 100, 20, 300, 10));

    public NearbyPlayers() {
        super("NearbyPlayers", "Список ближайших игроков: ник, здоровье и расстояние.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        List<Player> list = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Player p && p != mc.player && !p.isRemoved() && mc.player.distanceTo(p) <= range.get()) list.add(p);
        }
        if (list.isEmpty()) return;
        list.sort(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)));
        int w = mc.getWindow().getGuiScaledWidth() + ox();
        int y = right(150);
        int n = 0;
        for (Player p : list) {
            if (n++ >= max.asInt()) break;
            String s = p.getName().getString() + "  " + Math.round(p.getHealth() + p.getAbsorptionAmount()) + " HP  " + (int) mc.player.distanceTo(p) + " м";
            int tw = Draw.width(s);
            Draw.panel(g, w - tw - 8, y - 1, w - 3, y + 10, Draw.bg(), false);
            Draw.rect(g, w - 4, y - 1, w - 3, y + 10, Draw.healthColor(p.getHealth(), p.getMaxHealth()));
            Draw.text(g, s, w - tw - 6, y, Draw.WHITE);
            y += 12;
        }
    }
}

package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.TargetManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Список ближайших игроков одной аккуратной панелью: колонки ник / HP / метры, цветная точка здоровья. */
public class NearbyPlayers extends HudModule implements Config.DefaultOn {
    private final NumSetting max = add(new NumSetting("Максимум строк", "Сколько ближайших игроков показывать в списке.", 5, 1, 12, 1));
    private final NumSetting range = add(new NumSetting("Дальность", "Игроки дальше этого расстояния не показываются (блоки).", 64, 16, 300, 8));

    public NearbyPlayers() {
        super("NearbyPlayers", "Список ближайших игроков: ник, здоровье и расстояние.");
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        List<Player> list = new ArrayList<>();
        double r2 = range.get() * range.get();
        for (Player p : mc.level.players()) {
            if (p != mc.player && !p.isRemoved() && !p.isSpectator() && mc.player.distanceToSqr(p) <= r2) list.add(p);
        }
        if (list.isEmpty()) return;
        list.sort(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)));
        int n = Math.min(list.size(), max.asInt());

        int nameW = 0, hpW = Draw.width("00"), distW = Draw.width(Lang.t("000 м"));
        for (int i = 0; i < n; i++) nameW = Math.max(nameW, Draw.width(Draw.trim(list.get(i).getName().getString(), 90)));
        String head = Lang.t("Рядом") + (list.size() > n ? "  " + n + "/" + list.size() : "  " + list.size());
        int inner = Math.max(Draw.width(head), 8 + nameW + 8 + hpW + 8 + distW);
        int pw = inner + 12;
        int right = mc.getWindow().getGuiScaledWidth() - 4 + ox();
        int x = right - pw;
        int y = right(150);
        int ph = 15 + n * 11 + 2;
        Draw.panel(g, x, y, right, y + ph, Draw.bg());
        Draw.textBold(g, head, x + 6, y + 4, Draw.accent());
        int yy = y + 16;
        for (int i = 0; i < n; i++) {
            Player p = list.get(i);
            float hp = p.getHealth() + p.getAbsorptionAmount();
            int hc = Draw.healthColor(p.getHealth(), p.getMaxHealth());
            boolean target = TargetManager.isTarget(p);
            if (target) Draw.rect(g, x + 2, yy - 1, right - 2, yy + 10, 0x30FF4040);
            Draw.circle(g, x + 5, yy + 2, 5, hc);
            Draw.text(g, Draw.trim(p.getName().getString(), 90), x + 14, yy, target ? 0xFFFF8080 : Draw.WHITE);
            String hs = String.valueOf(Math.round(hp));
            String ds = (int) Math.sqrt(mc.player.distanceToSqr(p)) + Lang.t(" м");
            Draw.textRight(g, ds, right - 6, yy, Draw.GRAY);
            Draw.textRight(g, hs, right - 6 - distW - 8 + hpW, yy, hc);
            yy += 11;
        }
    }
}

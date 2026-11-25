package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.PlayerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Адрес сервера, примерный TPS, пинг, онлайн и время сессии. */
public class ServerInfo extends HudModule {
    private static long lastTimePacket;
    private static double tps = 20.0;

    private final BoolSetting address = add(new BoolSetting("Адрес", "IP сервера (или \"Одиночная игра\").", true));
    private final BoolSetting showTps = add(new BoolSetting("TPS", "Примерный TPS сервера (по пакетам времени, раз в секунду).", true));
    private final BoolSetting showPing = add(new BoolSetting("Пинг", "Твой пинг до сервера.", true));
    private final BoolSetting online = add(new BoolSetting("Онлайн", "Сколько игроков в списке Tab.", true));
    private final BoolSetting session = add(new BoolSetting("Время сессии", "Сколько ты уже играешь на сервере.", false));

    private long joinTime;
    private Object lastLevel;

    public ServerInfo() {
        super("Server Info", "Адрес сервера, TPS, пинг, онлайн и время сессии (строка в левом нижнем углу).");
    }

    /** Вызывается из ClientPacketListenerMixin: сервер присылает время раз в 20 тиков. */
    public static void onTimePacket() {
        long now = System.nanoTime();
        if (lastTimePacket != 0) {
            double sec = (now - lastTimePacket) / 1.0e9;
            if (sec > 0.05 && sec < 20) {
                double cur = Math.min(20.0, 20.0 / sec);
                tps += (cur - tps) * 0.35;
            }
        }
        lastTimePacket = now;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level != lastLevel) {
            if (lastLevel == null || mc.level == null) {
                joinTime = System.currentTimeMillis();
                tps = 20.0;
                lastTimePacket = 0;
            }
            lastLevel = mc.level;
        }
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        List<String> lines = new ArrayList<>();
        ServerData sd = mc.getCurrentServer();
        if (address.get()) lines.add(sd != null ? "Сервер: " + sd.ip : "Одиночная игра");
        if (showTps.get()) {
            String col = tps >= 18 ? "§a" : tps >= 14 ? "§e" : "§c";
            lines.add(String.format(Locale.ROOT, "TPS: %s%.1f", col, tps));
        }
        if (showPing.get() && sd != null) lines.add("Пинг: " + PlayerUtil.ping(mc) + " мс");
        if (online.get() && mc.getConnection() != null) lines.add("Онлайн: " + mc.getConnection().getOnlinePlayers().size());
        if (session.get()) {
            long s = (System.currentTimeMillis() - joinTime) / 1000;
            lines.add(String.format(Locale.ROOT, "Сессия: %d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        }
        if (lines.isEmpty()) return;
        // Одна строка в левом нижнем углу (там свободно: чат выше, хотбар по центру)
        String line = String.join(" §7|§r ", lines);
        int h = mc.getWindow().getGuiScaledHeight();
        int x = 4 + ox(), y = h - 12 + oy();
        int tw = Draw.width(line);
        Draw.rect(g, x - 2, y - 2, x + tw + 2, y + 10, 0x80000000);
        Draw.text(g, line, x, y, Draw.WHITE);
    }
}

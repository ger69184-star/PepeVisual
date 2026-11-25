package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;
import ru.visual.client.module.Config;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Cps;
import ru.visual.client.util.PlayerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InfoHud extends HudModule implements Config.DefaultOn {
    private final BoolSetting coords = add(new BoolSetting("Координаты", "Показывает твои координаты XYZ.", true));
    private final BoolSetting nether = add(new BoolSetting("Координаты Незера", "Пересчёт координат между Верхним миром и Незером.", true));
    private final BoolSetting facing = add(new BoolSetting("Направление", "Сторона света, в которую ты смотришь.", true));
    private final BoolSetting speed = add(new BoolSetting("Скорость", "Скорость движения в блоках в секунду.", true));
    private final BoolSetting ping = add(new BoolSetting("Пинг", "Задержка до сервера в миллисекундах.", true));
    private final BoolSetting cps = add(new BoolSetting("CPS", "Сколько раз в секунду ты нажимаешь левую | правую кнопку мыши.", false));

    private double lastX, lastZ, bps;

    public InfoHud() {
        super("Info", "Информация в левом верхнем углу: координаты, направление, скорость, пинг и CPS.");
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        double dx = mc.player.getX() - lastX, dz = mc.player.getZ() - lastZ;
        double cur = Math.sqrt(dx * dx + dz * dz) * 20.0;
        if (cur > 100) cur = 0; // телепортация / первый тик
        bps += (cur - bps) * 0.3;
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();

    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        List<String> lines = new ArrayList<>();
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        if (coords.get()) lines.add(String.format(Locale.ROOT, "XYZ: %.1f %.1f %.1f", x, y, z));
        if (nether.get()) {
            boolean inNether = mc.level.dimension() == Level.NETHER;
            double k = inNether ? 8.0 : 1.0 / 8.0;
            lines.add(String.format(Locale.ROOT, "%s: %.1f %.1f", inNether ? "Верхний мир" : "Незер", x * k, z * k));
        }
        if (facing.get()) lines.add("Смотришь: " + facingName(mc.player.getDirection()) + String.format(Locale.ROOT, " (%.0f°)", net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot())));
        if (speed.get()) lines.add(String.format(Locale.ROOT, "Скорость: %.1f б/с", bps));
        if (ping.get()) lines.add("Пинг: " + PlayerUtil.ping(mc) + " мс");
        if (cps.get()) lines.add("CPS: " + Cps.left() + " | " + Cps.right());

        int yy = 22 + oy();
        int xx = 4 + ox();
        int top = yy - 1, right = xx;
        for (String s : lines) {
            Draw.rect(g, xx, yy - 1, xx + 4 + Draw.width(s), yy + 10, 0x80000000);
            Draw.text(g, s, xx + 2, yy, Draw.WHITE);
            right = Math.max(right, xx + 4 + Draw.width(s));
            yy += 12;
        }
        if (!lines.isEmpty()) bounds(xx, top, right, yy - 2);
    }

    private static String facingName(net.minecraft.core.Direction d) {
        return switch (d) {
            case NORTH -> "Север (-Z)";
            case SOUTH -> "Юг (+Z)";
            case WEST -> "Запад (-X)";
            case EAST -> "Восток (+X)";
            case UP -> "Вверх";
            case DOWN -> "Вниз";
        };
    }
}

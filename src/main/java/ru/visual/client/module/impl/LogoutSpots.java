package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;
import ru.visual.client.util.R3D;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Отмечает место, где игрок вышел с сервера рядом с тобой. */
public class LogoutSpots extends Module {
    private record Seen(String name, AABB box, Vec3 pos) {}

    private record Spot(String name, AABB box, Vec3 pos, long time) {}

    private final NumSetting keep = add(new NumSetting("Хранить (мин)", "Сколько минут показывать отметку.", 10, 1, 60, 1));

    private final Map<UUID, Seen> seen = new HashMap<>();
    private final Map<UUID, Spot> spots = new HashMap<>();
    private ClientLevel lastLevel;

    public LogoutSpots() {
        super("LogoutSpots", "Показывает призрачную рамку там, где игрок рядом с тобой вышел с сервера (с ником и временем).", Category.UTILITY);
    }

    @Override
    protected void onDisable() {
        seen.clear();
        spots.clear();
    }

    @Override
    public String info() {
        return spots.isEmpty() ? null : String.valueOf(spots.size());
    }

    @Override
    public boolean tickOutsideWorld() {
        return true;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            seen.clear();
            spots.clear();
            lastLevel = null;
            return;
        }
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            seen.clear();
            spots.clear();
        }
        Map<UUID, Seen> now = new HashMap<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            now.put(p.getUUID(), new Seen(p.getName().getString(), p.getBoundingBox(), p.position()));
            spots.remove(p.getUUID()); // вернулся
        }
        for (Map.Entry<UUID, Seen> e : seen.entrySet()) {
            if (now.containsKey(e.getKey())) continue;
            // пропал из мира: если его нет и в списке игроков (Tab) - значит вышел, а не просто ушёл далеко
            if (mc.getConnection().getPlayerInfo(e.getKey()) == null) {
                Seen s = e.getValue();
                spots.put(e.getKey(), new Spot(s.name, s.box, s.pos, System.currentTimeMillis()));
            }
        }
        seen.clear();
        seen.putAll(now);
        long life = (long) (keep.get() * 60_000);
        Iterator<Spot> it = spots.values().iterator();
        while (it.hasNext()) if (System.currentTimeMillis() - it.next().time > life) it.remove();
    }

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        double t = System.currentTimeMillis() / 1000.0;
        for (Spot s : spots.values()) {
            int a = (int) (40 + 20 * Math.sin(t * 3));
            R3D.boxFilled(s.box, Draw.withAlpha(0xFF5050, a));
            R3D.boxOutline(s.box, 0xE0FF5050, 2f);
            // луч вверх - видно издалека
            R3D.line(s.pos.x, s.box.maxY, s.pos.z, s.pos.x, s.box.maxY + 24, s.pos.z, 0x80FF5050, 1.5f);
            R3D.circle(s.pos.x, s.box.minY + 0.02, s.pos.z, 0.6, 32, 0xA0FF5050, 2f);
        }
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        double[] o = new double[3];
        for (Spot s : spots.values()) {
            if (!Projection.project(s.pos.x, s.box.maxY + 0.4, s.pos.z, o)) continue;
            long min = (System.currentTimeMillis() - s.time) / 60000;
            int dist = (int) mc.player.position().distanceTo(s.pos);
            String text = s.name + Lang.t(" вышел ") + (min == 0 ? Lang.t("только что") : min + Lang.t(" мин назад")) + " · " + dist + Lang.t("м");
            Draw.pill(g, text, (int) o[0], (int) o[1] - 6, 0xFFFF7070);
        }
    }
}

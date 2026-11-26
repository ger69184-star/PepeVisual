package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.HudModule;
import ru.visual.client.util.Draw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Статистика за текущую игровую сессию. */
public class SessionStats extends HudModule {
    private static SessionStats instance;

    private long start;
    private int blocks, kills, deaths;
    private double walked;
    private Vec3 lastPos;
    private boolean wasDead;
    private ClientLevel lastLevel;
    /** Кого ты недавно бил: если умрёт в течение 3 секунд - засчитываем. */
    private final Map<Entity, Long> recentHits = new HashMap<>();

    public SessionStats() {
        super("SessionStats", "Статистика сессии: время в игре, сломанные блоки, убитые мобы, смерти и пройденное расстояние.");
        instance = this;
    }

    /** Вызывается из ClientPlayerBlockBreakEvents. */
    public static void onBlockBroken() {
        if (instance != null && instance.isEnabled()) instance.blocks++;
    }

    @Override
    public void onAttack(Entity target) {
        if (target instanceof LivingEntity) recentHits.put(target, System.currentTimeMillis());
    }

    private void reset() {
        start = System.currentTimeMillis();
        blocks = kills = deaths = 0;
        walked = 0;
        lastPos = null;
        recentHits.clear();
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    public boolean tickOutsideWorld() {
        return true;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            lastLevel = null;
            return;
        }
        if (lastLevel == null) reset(); // зашли в мир/на сервер - новая сессия
        if (mc.level != lastLevel) lastPos = null; // смена измерения
        lastLevel = mc.level;
        if (start == 0) reset();

        Vec3 p = mc.player.position();
        if (lastPos != null) {
            double d = Math.sqrt(Math.pow(p.x - lastPos.x, 2) + Math.pow(p.z - lastPos.z, 2));
            if (d < 10) walked += d; // телепорты не считаем
        }
        lastPos = p;

        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead) deaths++;
        wasDead = dead;

        long now = System.currentTimeMillis();
        List<Entity> gone = new ArrayList<>();
        for (Map.Entry<Entity, Long> e : recentHits.entrySet()) {
            Entity en = e.getKey();
            if (en instanceof LivingEntity le && le.isDeadOrDying()) {
                kills++;
                gone.add(en);
            } else if (now - e.getValue() > 3000 || en.isRemoved()) {
                gone.add(en);
            }
        }
        for (Entity en : gone) recentHits.remove(en);
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (mc.options.hideGui) return;
        String[][] rows = {
                {"Время", time(System.currentTimeMillis() - start)},
                {"Блоков", String.valueOf(blocks)},
                {"Убийств", String.valueOf(kills)},
                {"Смертей", String.valueOf(deaths)},
                {"Пройдено", walked >= 1000 ? String.format("%.1f км", walked / 1000) : (int) walked + " м"},
        };
        int w = 0;
        for (String[] r : rows) w = Math.max(w, Draw.width(r[0]) + Draw.width(r[1]) + 16);
        w = Math.max(w, Draw.width("Сессия") + 12);
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = 4 + ox(), y = left(sh / 2 + 40);
        int h = 14 + rows.length * 10 + 3;
        Draw.panel(g, x, y, x + w, y + h, Draw.bg());
        Draw.text(g, "Сессия", x + 6, y + 3, Draw.accent());
        for (int i = 0; i < rows.length; i++) {
            int ry = y + 14 + i * 10;
            Draw.text(g, rows[i][0], x + 6, ry, Draw.GRAY);
            Draw.textRight(g, rows[i][1], x + w - 6, ry, Draw.WHITE);
        }
    }
}

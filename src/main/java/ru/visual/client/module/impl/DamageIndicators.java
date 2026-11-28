package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DamageIndicators extends Module {
    private record Popup(Vec3 pos, String text, int color, long t) {}

    private final BoolSetting heal = add(new BoolSetting("Лечение", "Показывать и числа лечения (зелёные).", true));
    private final BoolSetting self = add(new BoolSetting("Свой урон", "Показывать числа и над тобой.", false));

    private final Map<Integer, Float> hp = new HashMap<>();
    private final List<Popup> popups = new ArrayList<>();

    public DamageIndicators() {
        super("DamageIndicators", "Всплывающие числа урона над существами, когда им наносят урон.", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        hp.clear();
        popups.clear();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || le.isRemoved() || le.getType() == EntityType.ARMOR_STAND) continue;
            if (le == mc.player && !self.get()) continue;
            if (mc.player.distanceTo(le) > 40) continue;
            float cur = le.getHealth() + le.getAbsorptionAmount();
            Float prev = hp.put(le.getId(), cur);
            if (prev == null) continue;
            float d = cur - prev;
            if (Math.abs(d) < 0.5f) continue;
            if (d > 0 && !heal.get()) continue;
            String text = (d < 0 ? "-" : "+") + String.format(Locale.ROOT, "%.1f", Math.abs(d));
            popups.add(new Popup(new Vec3(le.getX(), le.getY() + le.getBbHeight() + 0.2, le.getZ()), text,
                    d < 0 ? 0xFF4040 : 0x50FF70, now));
        }
        if (hp.size() > 400) hp.clear();
        popups.removeIf(p -> now - p.t > 1200);
        while (popups.size() > 40) popups.remove(0);
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        long now = System.currentTimeMillis();
        double[] o = new double[3];
        for (Popup p : popups) {
            double age = (now - p.t) / 1200.0;
            if (!Projection.project(p.pos.x, p.pos.y + age * 0.9, p.pos.z, o)) continue;
            Draw.textCentered(g, p.text, (int) o[0], (int) o[1], Draw.withAlpha(p.color, (int) ((1.0 - age) * 255)));
        }
    }

    @Override
    public void onWorldChange(Minecraft mc) {
        hp.clear();
        popups.clear();
    }
}

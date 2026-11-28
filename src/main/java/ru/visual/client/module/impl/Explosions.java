package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;
import ru.visual.client.util.R3D;

/** Таймер подожжённого динамита и предупреждение о шипящем крипере. */
public class Explosions extends Module implements Config.DefaultOn {
    private final BoolSetting tnt = add(new BoolSetting("Динамит", "Секунды до взрыва над подожжённым динамитом.", true));
    private final BoolSetting creepers = add(new BoolSetting("Криперы", "Секунды до взрыва над шипящим крипером.", true));
    private final BoolSetting radius = add(new BoolSetting("Радиус взрыва", "Круг на земле - примерная зона поражения.", true));

    public Explosions() {
        super("Explosions", "Таймер до взрыва динамита и предупреждение о шипящих криперах с радиусом взрыва.", Category.VISUAL);
    }

    private static int fuseColor(float f) {
        // f: 1 - только что подожгли, 0 - сейчас взорвётся
        return Draw.mix(0xFFFF3030, 0xFF50FF70, f);
    }

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        if (!radius.get()) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            Vec3 p = e.getPosition(pt);
            if (tnt.get() && e instanceof PrimedTnt t) {
                float f = Math.min(1f, t.getFuse() / 80f);
                R3D.circle(p.x, p.y + 0.05, p.z, 4.0, 48, Draw.withAlpha(fuseColor(f), 150), 2f);
            } else if (creepers.get() && e instanceof Creeper c && c.getSwelling(pt) > 0.01f) {
                float r = c.isPowered() ? 6f : 3f;
                R3D.circle(p.x, p.y + 0.05, p.z, r, 40, Draw.withAlpha(fuseColor(1f - c.getSwelling(pt)), 170), 2.2f);
            }
        }
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        double[] o = new double[3];
        for (Entity e : mc.level.entitiesForRendering()) {
            Vec3 p = e.getPosition(pt);
            String text = null;
            int col = Draw.WHITE;
            if (tnt.get() && e instanceof PrimedTnt t) {
                int fuse = t.getFuse();
                text = String.format(Lang.t("%.1f с"), Math.max(0, fuse - pt) / 20f);
                col = fuseColor(Math.min(1f, fuse / 80f));
            } else if (creepers.get() && e instanceof Creeper c) {
                float s = c.getSwelling(pt);
                if (s <= 0.01f) continue;
                // getSwelling = swell / (maxSwell - 2), взрыв при swell >= maxSwell (30 тиков = 1.5 с)
                float left = (30f - s * 28f) / 20f;
                text = String.format(Lang.t("Крипер %.1f с"), Math.max(0f, left));
                col = fuseColor(1f - s);
            }
            if (text == null) continue;
            if (!Projection.project(p.x, p.y + e.getBbHeight() + 0.5, p.z, o)) continue;
            int w = Draw.width(text) + 8;
            int x = (int) o[0] - w / 2, y = (int) o[1] - 6;
            Draw.panel(g, x, y, x + w, y + 12, Draw.bg());
            Draw.textCentered(g, text, (int) o[0], y + 2, col);
        }
    }
}

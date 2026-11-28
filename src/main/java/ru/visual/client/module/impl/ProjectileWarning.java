package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.R3D;

/** Предупреждение о летящем снаряде рядом: надпись с дистанцией и направлением, 3D-маркер, звук. */
public class ProjectileWarning extends Module {
    private final NumSetting radius = add(new NumSetting("Радиус", "За сколько блоков предупреждать о снаряде.", 10, 1, 30, 1));
    private final BoolSetting text = add(new BoolSetting("Надпись", "Надпись с дистанцией и направлением к снаряду.", true));
    private final BoolSetting marker = add(new BoolSetting("3D-маркер", "Кружок над снарядом в мире (видно только тебе).", true));
    private final BoolSetting sound = add(new BoolSetting("Звук", "Звук, когда снаряд заходит в радиус.", true));

    private Entity target;
    private Entity lastWarned;
    private long soundAt;

    public ProjectileWarning() {
        super("ProjectileWarning", "Предупреждение о летящем снаряде: стрела, трезубец, шар, снаряд крипера - надпись, 3D-маркер и звук.", Category.VISUAL);
    }

    private static boolean isProjectile(Entity e) {
        return e instanceof Projectile;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        Entity found = null;
        double best = radius.get() * radius.get();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!isProjectile(e) || e.isRemoved()) continue;
            double d = e.distanceToSqr(mc.player);
            if (d <= best) {
                best = d;
                found = e;
            }
        }
        target = found;
        if (found != null && found != lastWarned && sound.get() && System.currentTimeMillis() - soundAt > 300) {
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 0.8f);
            }
            lastWarned = found;
            soundAt = System.currentTimeMillis();
        }
        if (found == null) lastWarned = null;
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (target == null || !text.get() || mc.options.hideGui || mc.player == null) return;
        double dx = target.getX() - mc.player.getX(), dz = target.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        String dir = Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0 ? Lang.t("Восток") : Lang.t("Запад"))
                : (dz >= 0 ? Lang.t("Юг") : Lang.t("Север"));
        String s = Lang.t("Снаряд: ") + Math.round(dist) + Lang.t(" м · ") + dir;
        Draw.pill(g, s, mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2 - 30, 0xFFFF5050);
    }

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        if (!marker.get() || target == null || !R3D.ready()) return;
        R3D.circle(target.getX(), target.getY() + 0.5, target.getZ(), 0.45, 14, 0xFFFF5050, 2f);
    }

    @Override
    public void onWorldChange(Minecraft mc) {
        target = null;
        lastWarned = null;
    }
}

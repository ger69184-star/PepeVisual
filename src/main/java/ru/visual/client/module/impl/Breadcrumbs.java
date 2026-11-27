package ru.visual.client.module.impl;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.R3D;

import java.util.ArrayDeque;

/** "Хлебные крошки": линия по земле там, где ты прошёл. Помогает найти дорогу назад из пещеры. */
public class Breadcrumbs extends Module {
    private final NumSetting length = add(new NumSetting("Длина", "Сколько точек хранить (точка каждые полблока).", 600, 50, 3000, 50));
    private final ColorSetting color = add(new ColorSetting("Цвет", "Цвет линии. ПКМ - радужная.", 0x8A63FF));

    private final ArrayDeque<Vec3> points = new ArrayDeque<>();
    private ResourceKey<Level> dim;

    public Breadcrumbs() {
        super("Breadcrumbs", "Рисует линию по пути, который ты прошёл, - чтобы не заблудиться в пещере или по дороге домой.", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        points.clear();
    }

    @Override
    public boolean tickOutsideWorld() {
        return true;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            points.clear();
            return;
        }
        if (dim != mc.level.dimension()) {
            dim = mc.level.dimension();
            points.clear();
        }
        Vec3 p = mc.player.position().add(0, 0.1, 0);
        Vec3 last = points.peekLast();
        if (last != null && last.distanceToSqr(p) > 100) points.clear(); // телепорт
        if (last == null || last.distanceToSqr(p) > 0.25) points.addLast(p);
        while (points.size() > length.asInt()) points.pollFirst();
    }

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        int n = points.size();
        if (n < 2) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        double maxD = 128 * 128;
        Vec3 prev = null;
        int i = 0;
        for (Vec3 p : points) {
            // далёкие отрезки не рисуем - линия на 3000 точек иначе съедает кадры
            if (prev != null && p.distanceToSqr(cam) < maxD) {
                float f = (float) i / n;
                int c = color.argb(f * 2);
                R3D.line(prev, p, Draw.withAlpha(c, (int) (40 + 200 * f)), f > 0.97f ? 3f : 2f);
            }
            prev = p;
            i++;
        }
    }
}

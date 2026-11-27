package ru.visual.client.module.impl;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Lang;
import ru.visual.client.util.R3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Кто в тебя целится: если луч взгляда игрока рядом проходит через тебя, сверху экрана появляется
 * предупреждение (ник, что в руке, дистанция), а от его глаз к тебе тянется красная линия.
 * Особенно полезно против лучников и арбалетчиков - видно, кто натянул лук в твою сторону.
 */
public class AimAlert extends Module {
    private final NumSetting range = add(new NumSetting("Дальность", "Проверять игроков в этом радиусе (блоков).", 48, 8, 128, 4));
    private final BoolSetting rangedOnly = add(new BoolSetting("Только дальний бой", "Предупреждать только об игроках с луком, арбалетом, трезубцем или жемчугом в руке.", false));
    private final BoolSetting lines = add(new BoolSetting("Линии взгляда", "3D-линия от глаз игрока, который смотрит на тебя.", true));
    private final BoolSetting hud = add(new BoolSetting("Предупреждение", "Надпись сверху экрана: кто целится и с какого расстояния.", true));
    private final BoolSetting sound = add(new BoolSetting("Звук", "Тихий сигнал, когда в тебя начали целиться из лука или арбалета.", false));

    private record Aim(Player p, double dist, boolean drawing) {}

    private final List<Aim> aims = new ArrayList<>();
    private final java.util.Set<java.util.UUID> warned = new java.util.HashSet<>();

    public AimAlert() {
        super("AimAlert", "Предупреждает, когда игрок рядом целится в тебя (особенно из лука): надпись с ником и дистанцией и линия взгляда.", Category.VISUAL);
    }

    @Override
    public void onTick(Minecraft mc) {
        aims.clear();
        if (mc.player == null || mc.level == null) return;
        Vec3 me = mc.player.getBoundingBox().getCenter();
        AABB box = mc.player.getBoundingBox().inflate(0.35);
        double r = range.get();
        java.util.Set<java.util.UUID> now = new java.util.HashSet<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator() || p.isDeadOrDying()) continue;
            double d = p.distanceTo(mc.player);
            if (d > r || d < 1.5) continue;
            ItemStack held = p.getMainHandItem();
            boolean rangedItem = held.is(Items.BOW) || held.is(Items.CROSSBOW) || held.is(Items.TRIDENT) || held.is(Items.ENDER_PEARL)
                    || p.getOffhandItem().is(Items.ENDER_PEARL);
            if (rangedOnly.get() && !rangedItem) continue;
            Vec3 eye = p.getEyePosition();
            Vec3 look = p.getViewVector(1f);
            Vec3 end = eye.add(look.scale(d + 2));
            // луч взгляда пересекает твой хитбокс (с небольшим запасом)?
            if (box.clip(eye, end).isEmpty() && !box.contains(eye)) continue;
            // и между вами нет стены
            if (mc.level.clip(new net.minecraft.world.level.ClipContext(eye, me, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            boolean drawing = p.isUsingItem() && (held.is(Items.BOW) || held.is(Items.TRIDENT)) || (held.is(Items.CROSSBOW) && net.minecraft.world.item.CrossbowItem.isCharged(held));
            aims.add(new Aim(p, d, drawing));
            if (drawing) {
                now.add(p.getUUID());
                if (sound.get() && !warned.contains(p.getUUID())) {
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 0.5f, 1.6f);
                }
            }
        }
        warned.retainAll(now);
        warned.addAll(now);
        aims.sort((a, b) -> Boolean.compare(b.drawing, a.drawing) != 0 ? Boolean.compare(b.drawing, a.drawing) : Double.compare(a.dist, b.dist));
    }

    @Override
    public void onWorld(WorldRenderContext ctx, float pt, Minecraft mc) {
        if (!lines.get() || aims.isEmpty() || mc.player == null) return;
        Vec3 me = mc.player.getPosition(pt).add(0, mc.player.getBbHeight() * 0.6, 0);
        for (Aim a : aims) {
            Vec3 eye = a.p.getEyePosition(pt);
            int col = a.drawing ? 0xFF4D5E : 0xFFB13D;
            int pulse = (int) (150 + 100 * Math.sin(System.currentTimeMillis() / 120.0));
            R3D.line(eye, me, Draw.withAlpha(col, a.drawing ? pulse : 130), a.drawing ? 2.5f : 1.5f);
        }
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (!hud.get() || aims.isEmpty() || mc.options.hideGui) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int y = 30;
        int shown = 0;
        for (Aim a : aims) {
            if (shown++ >= 3) break;
            String what = a.drawing ? Lang.of(" целится в тебя", " is aiming at you") : Lang.of(" смотрит на тебя", " is looking at you");
            String s = a.p.getName().getString() + what + "  " + (int) a.dist + Lang.of(" м", " m");
            int tw = Draw.width(s) + 26;
            int x1 = sw / 2 - tw / 2, x2 = x1 + tw;
            int col = a.drawing ? 0xFFFF4D5E : 0xFFFFB13D;
            float pulse = a.drawing ? (float) (0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 110.0)) : 0.5f;
            Draw.panel(g, x1, y, x2, y + 18, Draw.bg());
            Draw.round(g, x1, y + 2, x1 + 2, y + 16, 1, Draw.withAlpha(col, (int) (255 * pulse)));
            if (!a.p.getMainHandItem().isEmpty()) Draw.item(g, a.p.getMainHandItem(), x1 + 3, y + 1);
            Draw.text(g, s, x1 + 20, y + 5, col);
            y += 21;
        }
    }

    @Override
    public String info() {
        return aims.isEmpty() ? null : String.valueOf(aims.size());
    }
}

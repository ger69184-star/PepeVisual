package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import ru.visual.client.module.HudModule;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Lang;

import java.util.Locale;

/**
 * Комбо и дальность удара под прицелом: сколько ударов подряд ты нанёс, не получив урона, и с какой дистанции
 * был последний удар. Цифра комбо "подпрыгивает" при каждом ударе и гаснет через пару секунд.
 */
public class ComboHud extends HudModule {
    private final BoolSetting combo = add(new BoolSetting("Комбо", "Удары подряд без получения урона (сбрасывается, когда бьют тебя или через 3 сек).", true));
    private final BoolSetting reach = add(new BoolSetting("Дальность удара", "С какой дистанции был последний удар (в блоках, до хитбокса).", true));
    private final NumSetting minCombo = add(new NumSetting("Показывать с", "Показывать комбо, начиная с этого числа ударов.", 2, 1, 5, 1));
    private final NumSetting size = add(new NumSetting("Размер", "Размер надписи.", 1.0, 0.6, 2.0, 0.1));

    private int count;
    private long lastHit;
    private double lastReach;
    private int lastHurt;

    public ComboHud() {
        super("ComboHUD", "Комбо (удары подряд без урона) и дальность последнего удара прямо под прицелом.");
    }

    @Override
    public void onAttack(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(target instanceof LivingEntity)) return;
        long now = System.currentTimeMillis();
        if (now - lastHit > 3000) count = 0;
        // по одной и той же цели во время неуязвимости удар не засчитывается сервером
        if (((LivingEntity) target).hurtTime <= 2) count++;
        lastHit = now;
        lastReach = Math.sqrt(target.getBoundingBox().distanceToSqr(mc.player.getEyePosition()));
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        int ht = mc.player.hurtTime;
        if (ht > lastHurt && ht >= 9) count = 0; // по тебе попали - комбо сброшено
        lastHurt = ht;
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (mc.options.hideGui) return;
        long age = System.currentTimeMillis() - lastHit;
        if (mc.screen instanceof ru.visual.client.gui.HudEditorScreen && (age > 3000 || lastHit == 0)) {
            // пример в редакторе HUD, чтобы элемент можно было перетащить
            count = 4;
            lastReach = 2.87;
            lastHit = System.currentTimeMillis() - 500;
            age = 500;
        }
        if (age > 3000 || lastHit == 0) return;
        float alpha = age < 2200 ? 1f : 1f - (age - 2200) / 800f;
        StringBuilder sb = new StringBuilder();
        boolean showCombo = combo.get() && count >= minCombo.asInt();
        if (showCombo) sb.append(count).append(Lang.of(" комбо", " combo"));
        if (reach.get()) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(String.format(Locale.ROOT, "%.2f", lastReach)).append(Lang.of(" бл", " bl"));
        }
        if (sb.length() == 0) return;
        String s = sb.toString();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        float cx = sw / 2f + ox(), cy = sh / 2f + 14 + oy();
        // "подпрыгивание" сразу после удара
        float pop = age < 150 ? 1f + 0.25f * (1f - age / 150f) : 1f;
        float k = size.asFloat() * pop;
        int a = Math.max(8, (int) (255 * alpha));
        int col = count >= 5 && showCombo ? Draw.withAlpha(Draw.accent(), a) : Draw.withAlpha(0xFFFFFF, a);
        int tw = Draw.width(s);
        Draw.scaled(g, k, cx, cy + 4, () -> {
            int x = Math.round(cx - tw / 2f), y = Math.round(cy);
            Draw.round(g, x - 4, y - 2, x + tw + 4, y + 10, 3, Draw.withAlpha(0x0E0E14, a * 0x90 / 255));
            Draw.text(g, s, x, y, col);
        });
        int hw = Math.round((tw / 2f + 5) * size.asFloat());
        bounds(Math.round(cx) - hw, Math.round(cy) - 3, Math.round(cx) + hw, Math.round(cy + 11 * size.asFloat()));
    }
}

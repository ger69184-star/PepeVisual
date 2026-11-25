package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;

public class Fullbright extends Module {
    public Fullbright() {
        super("Fullbright", "Полная яркость: видно в пещерах и ночью (клиентский эффект ночного зрения).", Category.VISUAL);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        if (mc.player.getEffect(MobEffects.NIGHT_VISION) == null) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1_000_000, 0, false, false, false));
        }
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MobEffectInstance e = mc.player.getEffect(MobEffects.NIGHT_VISION);
        if (e != null && e.getDuration() > 500_000) mc.player.removeEffect(MobEffects.NIGHT_VISION);
    }
}

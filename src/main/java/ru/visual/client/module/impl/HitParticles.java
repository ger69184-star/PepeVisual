package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

import java.util.Random;

public class HitParticles extends Module {
    private final ModeSetting type = add(new ModeSetting("Частицы", "Какие частицы появляются при ударе.", "Крит",
            "Крит", "Магический крит", "Сердца", "Огонь", "Звёзды", "Тотем", "Синий огонь", "Ноты"));
    private final NumSetting count = add(new NumSetting("Количество", "Сколько частиц появляется за один удар.", 12, 2, 40, 1));
    private final Random rnd = new Random();

    public HitParticles() {
        super("HitParticles", "Красивые частицы вокруг существа, по которому ты ударил (видно только тебе).", Category.VISUAL);
    }

    @Override
    public void onAttack(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(target instanceof LivingEntity)) return;
        ParticleOptions opt = switch (type.get()) {
            case "Магический крит" -> ParticleTypes.ENCHANTED_HIT;
            case "Сердца" -> ParticleTypes.HEART;
            case "Огонь" -> ParticleTypes.FLAME;
            case "Звёзды" -> ParticleTypes.END_ROD;
            case "Тотем" -> ParticleTypes.TOTEM_OF_UNDYING;
            case "Синий огонь" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "Ноты" -> ParticleTypes.NOTE;
            default -> ParticleTypes.CRIT;
        };
        double w = target.getBbWidth(), h = target.getBbHeight();
        for (int i = 0; i < count.asInt(); i++) {
            mc.level.addParticle(opt,
                    target.getX() + (rnd.nextDouble() - 0.5) * w,
                    target.getY() + rnd.nextDouble() * h,
                    target.getZ() + (rnd.nextDouble() - 0.5) * w,
                    (rnd.nextDouble() - 0.5) * 0.4, rnd.nextDouble() * 0.3, (rnd.nextDouble() - 0.5) * 0.4);
        }
    }
}

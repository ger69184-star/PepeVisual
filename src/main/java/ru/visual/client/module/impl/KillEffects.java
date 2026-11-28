package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Эффект убийства: когда умирает тот, кого ты бил последние 3 секунды, на его месте бьёт молния
 * (только у тебя на экране, без урона и пожара) или вылетает облако частиц. Плюс звук.
 */
public class KillEffects extends Module {
    private final ModeSetting effect = add(new ModeSetting("Эффект", "Что показать на месте убитого.",
            "Молния", "Молния", "Души", "Тотем", "Кровь", "Взрыв", "Нет"));
    private final BoolSetting playersOnly = add(new BoolSetting("Только игроки", "Эффект только за убийство игроков (не мобов).", true));
    private final BoolSetting sound = add(new BoolSetting("Звук", "Звук при убийстве (у молнии - свой гром).", true));

    private final Map<Entity, Long> recent = new HashMap<>();
    private final Random random = new Random();
    private static int fakeId = -1_000_000;

    public KillEffects() {
        super("KillEffects", "Молния, души или облако частиц на месте убитого тобой игрока. Видно только тебе.", Category.VISUAL);
    }

    @Override
    public void onAttack(Entity target) {
        if (target instanceof LivingEntity && (!playersOnly.get() || target instanceof Player)) recent.put(target, System.currentTimeMillis());
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || recent.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Entity> gone = new ArrayList<>();
        for (Map.Entry<Entity, Long> e : recent.entrySet()) {
            Entity en = e.getKey();
            if (en instanceof LivingEntity le && le.isDeadOrDying()) {
                play(mc, le);
                gone.add(en);
            } else if (now - e.getValue() > 3000 || en.isRemoved()) {
                gone.add(en);
            }
        }
        for (Entity en : gone) recent.remove(en);
    }

    @Override
    protected void onDisable() {
        recent.clear();
    }

    private void play(Minecraft mc, LivingEntity le) {
        Vec3 p = le.position();
        double h = le.getBbHeight();
        switch (effect.get()) {
            case "Молния" -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(mc.level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.setVisualOnly(true);
                    bolt.setId(fakeId--); // отрицательный id - не пересечётся с сущностями сервера
                    bolt.snapTo(p.x, p.y, p.z);
                    mc.level.addEntity(bolt);
                }
                return; // гром молния играет сама
            }
            case "Души" -> burst(mc, ParticleTypes.SOUL, p, h, 30, 0.08, 0.12);
            case "Тотем" -> burst(mc, ParticleTypes.TOTEM_OF_UNDYING, p, h, 60, 0.4, 0.5);
            case "Кровь" -> burst(mc, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()), p, h, 50, 0.2, 0.3);
            case "Взрыв" -> {
                mc.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + h / 2, p.z, 0, 0, 0);
                burst(mc, ParticleTypes.FLAME, p, h, 30, 0.15, 0.15);
            }
            default -> {
                return;
            }
        }
        if (sound.get()) {
            mc.level.playLocalSound(p.x, p.y, p.z, SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.6f, false);
        }
    }

    private void burst(Minecraft mc, ParticleOptions type, Vec3 p, double h, int n, double spread, double up) {
        for (int i = 0; i < n; i++) {
            double vx = (random.nextDouble() - 0.5) * spread * 2, vz = (random.nextDouble() - 0.5) * spread * 2;
            double vy = random.nextDouble() * up;
            mc.level.addParticle(type, p.x + (random.nextDouble() - 0.5) * 0.6, p.y + random.nextDouble() * h, p.z + (random.nextDouble() - 0.5) * 0.6, vx, vy, vz);
        }
    }

    @Override
    public void onWorldChange(Minecraft mc) {
        recent.clear();
    }
}

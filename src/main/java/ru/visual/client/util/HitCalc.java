package ru.visual.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Примерный расчёт: сколько ударов до смерти цели с учётом оружия, брони, зачарований и сопротивления. */
public final class HitCalc {
    public record Result(int hits, int critHits, double damage, double critDamage, double hp) {}

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private HitCalc() {}

    public static Result calc(Minecraft mc, LivingEntity target) {
        double base = mc.player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack hand = mc.player.getMainHandItem();
        int sharp = enchantLevel(hand, true);
        if (sharp > 0) base += 0.5 * sharp + 0.5;
        // Сила / Слабость уже входят в атрибут ATTACK_DAMAGE, поэтому отдельно их не считаем

        double armor = target.getArmorValue();
        double tough = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

        // Защита (Protection): каждый уровень = 1 очко, каждое очко снимает 4% урона, максимум 20 очков
        int epf = 0;
        for (EquipmentSlot s : ARMOR) epf += enchantLevel(target.getItemBySlot(s), false);
        double protection = 1.0 - Math.min(20, epf) * 0.04;

        double resist = 1.0;
        MobEffectInstance res = target.getEffect(MobEffects.RESISTANCE);
        if (res != null) resist = Math.max(0.0, 1.0 - 0.2 * (res.getAmplifier() + 1));

        double dmg = after(base, armor, tough) * protection * resist;
        double crit = after(base * 1.5, armor, tough) * protection * resist;
        double hp = target.getHealth() + target.getAbsorptionAmount();
        return new Result(count(hp, dmg), count(hp, crit), dmg, crit, hp);
    }

    private static double after(double damage, double armor, double toughness) {
        double f = Math.min(20.0, Math.max(armor / 5.0, armor - damage / (2.0 + toughness / 4.0)));
        return damage * (1.0 - f / 25.0);
    }

    private static int count(double hp, double dmg) {
        if (dmg <= 0.01) return 999;
        return (int) Math.max(1, Math.ceil(hp / dmg - 1e-6));
    }

    /** Сумма уровней: sharpness=true - острота оружия, иначе - защита на предмете брони. */
    private static int enchantLevel(ItemStack st, boolean sharpness) {
        if (st.isEmpty()) return 0;
        ItemEnchantments ench = st.getEnchantments();
        int level = 0;
        for (var holder : ench.keySet()) {
            if (sharpness && holder.is(Enchantments.SHARPNESS)) level = ench.getLevel(holder);
            if (!sharpness && holder.is(Enchantments.PROTECTION)) level = ench.getLevel(holder);
        }
        return level;
    }
}

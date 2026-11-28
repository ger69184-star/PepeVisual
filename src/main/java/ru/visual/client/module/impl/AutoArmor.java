package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Human;

/**
 * Сам надевает броню из инвентаря: в пустые слоты, а если включено - заменяет на более прочную (броня + твёрдость + защита).
 * Не трогает элитры на спине и вещи с проклятием несъёмности. Работает без открытого инвентаря или в инвентаре игрока.
 */
public class AutoArmor extends Module {
    private final ru.visual.client.util.Human.Tune tune = humanTune(false, false);
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final BoolSetting upgrade = add(new BoolSetting("Заменять на лучшую", "Если в инвентаре есть броня лучше надетой - поменять (старая ляжет на её место).", true));
    private final BoolSetting keepElytra = add(new BoolSetting("Не снимать элитры", "Элитры на спине не меняются на нагрудник.", true));
    private final NumSetting minDurability = add(new NumSetting("Мин. прочность (%)", "Не надевать почти сломанные вещи.", 5, 0, 50, 1));
    private final NumSetting delay = add(new NumSetting("Задержка (тики)", "Пауза между вещами (20 тиков = 1 сек). В продвинутом режиме - средняя: каждая пауза немного разная, как у человека.", 6, 2, 30, 1));
    private final BoolSetting onlyInventory = addIf(new BoolSetting("Только в открытом инвентаре", "Надевать броню, только когда ты сам открыл инвентарь (E) - самый незаметный вариант. Выкл: ещё и без окна, но только пока ты спокойно стоишь (не бежишь и не крутишь камерой).", false), tune::advanced);

    private int cooldown, react = -1;
    /** Клики по одному с паузами между ними. {слот, кнопка, тип} */
    private final java.util.ArrayDeque<int[]> clicks = new java.util.ArrayDeque<>();

    public AutoArmor() {
        super("AutoArmor", "Сам надевает броню из инвентаря и меняет её на более прочную. Элитры и проклятые вещи не трогает.", Category.HELPERS);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || p.getAbilities().instabuild) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof InventoryScreen)) {
            clicks.clear();
            return;
        }
        // начатую замену доводим до конца (иначе вещь останется "в курсоре")
        if (!clicks.isEmpty()) {
            int[] c = clicks.pollFirst();
            mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, c[0], c[1], ClickType.values()[c[2]], p);
            cooldown = clicks.isEmpty() ? Human.ticks(delay.get()) : tune.react(2, 5);
            return;
        }
        if (!p.containerMenu.getCarried().isEmpty()) return;
        boolean open = mc.screen instanceof InventoryScreen;
        boolean adv = tune.advanced();
        if (adv && !open && (onlyInventory.get() || !Human.standingStill(10))) {
            react = -1;
            return;
        }

        for (int s = 0; s < SLOTS.length; s++) {
            EquipmentSlot slot = SLOTS[s];
            int armorMenuSlot = 5 + s; // 5 шлем, 6 нагрудник, 7 поножи, 8 ботинки
            ItemStack worn = p.getItemBySlot(slot);
            if (!worn.isEmpty()) {
                if (!upgrade.get()) continue;
                if (EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) continue;
                if (keepElytra.get() && worn.is(Items.ELYTRA)) continue;
            }
            double current = worn.isEmpty() ? -1 : score(p, worn, slot);
            int best = -1;
            double bestScore = current;
            for (int i = 9; i <= 44; i++) {
                ItemStack st = p.inventoryMenu.getSlot(i).getItem();
                if (!fits(st, slot)) continue;
                double sc = score(p, st, slot);
                if (sc > bestScore + 0.01) {
                    bestScore = sc;
                    best = i;
                }
            }
            if (best < 0) continue;
            if (!adv) {
                // базовый режим (как раньше): все клики сразу
                int id = p.inventoryMenu.containerId;
                if (worn.isEmpty()) {
                    mc.gameMode.handleInventoryMouseClick(id, best, 0, ClickType.QUICK_MOVE, p);
                } else {
                    mc.gameMode.handleInventoryMouseClick(id, best, 0, ClickType.PICKUP, p);
                    mc.gameMode.handleInventoryMouseClick(id, armorMenuSlot, 0, ClickType.PICKUP, p);
                    mc.gameMode.handleInventoryMouseClick(id, best, 0, ClickType.PICKUP, p);
                }
                cooldown = delay.asInt();
                return;
            }
            // заметили вещь получше - не в тот же тик
            if (react < 0) {
                react = tune.react(4, 12);
                return;
            }
            if (react-- > 0) return;
            react = -1;
            if (worn.isEmpty()) {
                clicks.add(new int[]{best, 0, ClickType.QUICK_MOVE.ordinal()});
            } else {
                // взять новую вещь, положить в слот брони (старая окажется в курсоре), вернуть старую на место новой
                clicks.add(new int[]{best, 0, ClickType.PICKUP.ordinal()});
                clicks.add(new int[]{armorMenuSlot, 0, ClickType.PICKUP.ordinal()});
                clicks.add(new int[]{best, 0, ClickType.PICKUP.ordinal()});
            }
            return; // по одной вещи за раз
        }
        react = -1;
    }

    private boolean fits(ItemStack st, EquipmentSlot slot) {
        if (st.isEmpty()) return false;
        Equippable eq = st.get(DataComponents.EQUIPPABLE);
        if (eq == null || eq.slot() != slot) return false;
        if (st.is(Items.ELYTRA) || st.is(Items.CARVED_PUMPKIN) || st.getItem() instanceof net.minecraft.world.item.BlockItem) return false;
        if (EnchantmentHelper.has(st, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) return false;
        if (st.isDamageableItem() && st.getMaxDamage() > 0) {
            double left = 100.0 * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage();
            if (left < minDurability.get()) return false;
        }
        return true;
    }

    /** Насколько вещь хороша: броня + твёрдость + уровень защиты. Элитры на спине считаются "лучше всего" при keepElytra. */
    private double score(LocalPlayer p, ItemStack st, EquipmentSlot slot) {
        ItemAttributeModifiers mods = st.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double armor = mods.compute(Attributes.ARMOR, 0, slot);
        double tough = mods.compute(Attributes.ARMOR_TOUGHNESS, 0, slot);
        int prot = 0;
        try {
            prot = EnchantmentHelper.getItemEnchantmentLevel(p.level().registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), st);
        } catch (Exception ignored) {}
        return armor * 10 + tough * 4 + prot * 3;
    }
}

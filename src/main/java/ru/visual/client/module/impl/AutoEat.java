package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;

import java.util.Set;

/** Сам ест, когда голод падает ниже порога. Еду берёт из хотбара. */
public class AutoEat extends Module {
    private static final Set<Item> BAD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
            Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW, Items.CHICKEN, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);

    private final NumSetting hunger = add(new NumSetting("Есть при голоде", "Начинать есть, когда сытость (из 20) ниже этого значения.", 14, 1, 19, 1));
    private final BoolSetting lowHp = add(new BoolSetting("При низком здоровье", "Есть и при неполной сытости, если здоровье ниже половины (быстрее восстановится).", true));
    private final BoolSetting preferSat = add(new BoolSetting("Самую сытную", "Выбирать еду с наибольшим насыщением (иначе - самую дешёвую, которой хватит).", true));

    private int eatingSlot = -1, previous = -1, ticks;

    public AutoEat() {
        super("AutoEat", "Сам ест, когда проголодался: выбирает еду из хотбара (без гнилой плоти и золотых яблок), потом возвращает прежний слот.", Category.HELPERS);
    }

    @Override
    protected void onDisable() {
        stop(Minecraft.getInstance());
    }

    private void stop(Minecraft mc) {
        if (eatingSlot >= 0) {
            mc.options.keyUse.setDown(false);
            if (mc.player != null && previous >= 0) mc.player.getInventory().setSelectedSlot(previous);
        }
        eatingSlot = -1;
        previous = -1;
        ticks = 0;
    }

    @Override
    public boolean tickOutsideWorld() {
        return true;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) {
            eatingSlot = -1;
            return;
        }
        int food = p.getFoodData().getFoodLevel();
        if (eatingSlot >= 0) {
            ticks++;
            Inventory inv = p.getInventory();
            boolean stillFood = inv.getItem(eatingSlot).has(DataComponents.FOOD);
            // доели (сытость выросла / еда кончилась), открыли меню или слишком долго
            if (mc.screen != null || !stillFood || inv.getSelectedSlot() != eatingSlot || ticks > 60
                    || (ticks > 5 && !p.isUsingItem()) || food >= 20) {
                stop(mc);
            } else {
                mc.options.keyUse.setDown(true);
            }
            return;
        }
        if (mc.screen != null || p.isUsingItem() || p.getAbilities().instabuild) return;
        boolean need = food < hunger.asInt() || (lowHp.get() && p.getHealth() < p.getMaxHealth() / 2 && food < 20);
        if (!need) return;
        int slot = pick(p, 20 - food);
        if (slot < 0) return;
        previous = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        eatingSlot = slot;
        ticks = 0;
        mc.options.keyUse.setDown(true);
    }

    private int pick(LocalPlayer p, int missing) {
        int best = -1;
        float bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            FoodProperties f = s.get(DataComponents.FOOD);
            if (f == null || BAD.contains(s.getItem())) continue;
            float score;
            if (preferSat.get()) score = f.nutrition() + f.saturation();
            else score = f.nutrition() <= missing ? 100 - (missing - f.nutrition()) : 50 - f.nutrition();
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}

package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

/**
 * Тотем во второй руке. Сам кладёт тотем бессмертия из инвентаря во вторую руку - всегда или когда мало здоровья,
 * при долгом падении и полёте на элитрах. После срабатывания тотема сразу кладёт следующий.
 * В режиме "Мало HP" возвращает прежний предмет (щит, еду) на место, когда здоровье восстановилось.
 */
public class AutoTotem extends Module {
    private final ModeSetting mode = add(new ModeSetting("Когда", "Всегда - тотем во второй руке постоянно. Мало HP - только в опасности, остальное время там твой предмет.",
            "Всегда", "Всегда", "Мало HP"));
    private final NumSetting hearts = add(new NumSetting("Порог (сердца)", "В режиме \"Мало HP\": брать тотем, когда здоровье + золотые сердца ниже этого.", 6, 1, 10, 0.5));
    private final BoolSetting fall = add(new BoolSetting("При падении", "Брать тотем, если падаешь с большой высоты.", true));
    private final BoolSetting elytra = add(new BoolSetting("На элитрах", "Брать тотем во время полёта на элитрах (удар о стену).", true));
    private final BoolSetting giveBack = add(new BoolSetting("Вернуть предмет", "В режиме \"Мало HP\": когда опасность прошла, вернуть во вторую руку прежний предмет.", true));
    private final NumSetting delay = add(new NumSetting("Задержка (тики)", "Пауза между перекладываниями (20 тиков = 1 сек). 0 - мгновенно.", 1, 0, 10, 1));

    /** Слот меню инвентаря, откуда взят тотем (там теперь лежит прежний предмет второй руки). */
    private int swappedFrom = -1;
    private int cooldown;

    public AutoTotem() {
        super("AutoTotem", "Сам кладёт тотем бессмертия во вторую руку: всегда или при малом здоровье, падении и полёте на элитрах.", Category.HELPERS);
    }

    @Override
    protected void onDisable() {
        swappedFrom = -1;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || p.getAbilities().instabuild || p.isDeadOrDying()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        // в чужом окне (сундук и т.п.) номера слотов другие - ждём, пока закроют
        if (mc.screen != null && !(mc.screen instanceof InventoryScreen)) return;
        if (!p.containerMenu.getCarried().isEmpty()) return;

        boolean holding = p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        float hp = p.getHealth() + p.getAbsorptionAmount();
        boolean danger = hp <= hearts.get() * 2
                || (fall.get() && p.fallDistance > 6 && !p.onGround() && !p.isInWater())
                || (elytra.get() && p.isFallFlying());
        boolean need = mode.is("Всегда") || danger;

        if (need && !holding) {
            int slot = findTotem(p);
            if (slot < 0) return;
            swap(mc, p, slot);
            swappedFrom = mode.is("Всегда") ? -1 : slot;
            return;
        }
        // опасность прошла (с запасом 2 сердца) - возвращаем прежний предмет
        if (!need && holding && giveBack.get() && swappedFrom >= 0 && hp > hearts.get() * 2 + 4) {
            if (!p.inventoryMenu.getSlot(swappedFrom).getItem().is(Items.TOTEM_OF_UNDYING)) swap(mc, p, swappedFrom);
            swappedFrom = -1;
        }
        if (!holding && swappedFrom >= 0 && !need) swappedFrom = -1;
    }

    /** Слот меню инвентаря с тотемом: сначала основной инвентарь (9-35), потом хотбар (36-44). */
    private static int findTotem(LocalPlayer p) {
        for (int i = 9; i <= 44; i++) {
            if (p.inventoryMenu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private void swap(Minecraft mc, LocalPlayer p, int menuSlot) {
        // SWAP с кнопкой 40 - обмен слота со второй рукой (как клавиша F в инвентаре)
        mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot, 40, ClickType.SWAP, p);
        cooldown = delay.asInt();
    }

    @Override
    public String info() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) n += mc.player.getInventory().getItem(i).getCount();
        }
        return String.valueOf(n);
    }
}

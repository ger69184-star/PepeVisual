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
import ru.visual.client.util.Human;
import ru.visual.client.util.Keys;

/**
 * Тотем во второй руке. Сам кладёт тотем бессмертия из инвентаря во вторую руку - всегда или когда мало здоровья,
 * при долгом падении и полёте на элитрах. После срабатывания тотема сразу кладёт следующий.
 * В режиме "Мало HP" возвращает прежний предмет (щит, еду) на место, когда здоровье восстановилось.
 */
public class AutoTotem extends Module {
    private final Human.Tune tune = humanTune(false, false);
    private final ModeSetting mode = add(new ModeSetting("Когда", "Всегда - тотем во второй руке постоянно. Мало HP - только в опасности, остальное время там твой предмет.",
            "Всегда", "Всегда", "Мало HP"));
    private final NumSetting hearts = add(new NumSetting("Порог (сердца)", "В режиме \"Мало HP\": брать тотем, когда здоровье + золотые сердца ниже этого.", 6, 1, 10, 0.5));
    private final BoolSetting fall = add(new BoolSetting("При падении", "Брать тотем, если падаешь с большой высоты.", true));
    private final BoolSetting elytra = add(new BoolSetting("На элитрах", "Брать тотем во время полёта на элитрах (удар о стену).", true));
    private final BoolSetting giveBack = add(new BoolSetting("Вернуть предмет", "В режиме \"Мало HP\": когда опасность прошла, вернуть во вторую руку прежний предмет.", true));
    private final NumSetting delay = add(new NumSetting("Задержка (тики)", "Добавочная пауза перед перекладыванием (20 тиков = 1 сек). Реакция всегда немного разная, как у человека.", 1, 0, 10, 1));
    private final BoolSetting invMoving = addIf(new BoolSetting("Из инвентаря на ходу", "Брать тотем из основного инвентаря даже на бегу (без открытого инвентаря так не может ни один игрок - античит может заметить). Выкл: из хотбара - всегда, клавишей F; из инвентаря - только когда стоишь или открыл инвентарь.", false), tune::advanced);

    /** Слот меню инвентаря, откуда взят тотем (там теперь лежит прежний предмет второй руки), -1 - нет. */
    private int swappedFrom = -1;
    /** Слот хотбара, через который тотем взят клавишей F (там теперь прежний предмет второй руки), -1 - нет. */
    private int hotbarFrom = -1;
    private int cooldown, react = -1;
    /** Шаги "как игрок": 1 - нажать F, 2 - вернуть прежний слот хотбара. */
    private int step, stepWait, prevSlot = -1;

    public AutoTotem() {
        super("AutoTotem", "Сам кладёт тотем бессмертия во вторую руку: всегда или при малом здоровье, падении и полёте на элитрах.", Category.HELPERS);
    }

    @Override
    protected void onDisable() {
        swappedFrom = -1;
        hotbarFrom = -1;
        step = 0;
        react = -1;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || p.getAbilities().instabuild || p.isDeadOrDying()) return;
        if (!tune.advanced()) {
            step = 0;
            basicTick(mc, p);
            return;
        }
        if (step != 0) {
            runStep(mc, p);
            return;
        }
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
            // реакция: в опасности быстрее, в спокойной обстановке - не спеша
            if (react < 0) {
                react = (danger ? tune.react(1, 4) : tune.react(6, 16)) + delay.asInt();
                return;
            }
            if (react-- > 0) return;
            react = -1;
            int hb = hotbarTotem(p);
            if (hb >= 0 && mc.screen == null) {
                // как игрок: выбрать слот с тотемом и нажать F
                startHotbarSwap(p, hb);
                hotbarFrom = mode.is("Всегда") ? -1 : hb;
                swappedFrom = -1;
                return;
            }
            int slot = findTotem(p);
            if (slot < 0 || !inventoryAllowed(mc)) return;
            swap(mc, p, slot);
            swappedFrom = mode.is("Всегда") ? -1 : slot;
            hotbarFrom = -1;
            return;
        }
        react = -1;
        // опасность прошла (с запасом 2 сердца) - возвращаем прежний предмет
        if (!need && holding && giveBack.get() && hp > hearts.get() * 2 + 4) {
            if (hotbarFrom >= 0 && mc.screen == null) {
                if (!p.getInventory().getItem(hotbarFrom).is(Items.TOTEM_OF_UNDYING)) startHotbarSwap(p, hotbarFrom);
                hotbarFrom = -1;
                cooldown = Human.range(10, 30);
            } else if (swappedFrom >= 0 && inventoryAllowed(mc)) {
                if (!p.inventoryMenu.getSlot(swappedFrom).getItem().is(Items.TOTEM_OF_UNDYING)) swap(mc, p, swappedFrom);
                swappedFrom = -1;
            }
        }
        if (!holding && !need) {
            swappedFrom = -1;
            hotbarFrom = -1;
        }
    }

    /** Базовый режим (как раньше): тотем из любого слота инвентаря сразу, одним кликом, в любой момент. */
    private void basicTick(Minecraft mc, LocalPlayer p) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof InventoryScreen)) return;
        if (!p.containerMenu.getCarried().isEmpty()) return;
        boolean holding = p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        float hp = p.getHealth() + p.getAbsorptionAmount();
        boolean danger = hp <= hearts.get() * 2
                || (fall.get() && p.fallDistance > 6 && !p.onGround() && !p.isInWater())
                || (elytra.get() && p.isFallFlying());
        boolean need = mode.is("Всегда") || danger;
        if (need && !holding) {
            int slot = -1;
            for (int i = 9; i <= 44; i++) {
                if (p.inventoryMenu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) return;
            mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, slot, 40, ClickType.SWAP, p);
            cooldown = delay.asInt();
            swappedFrom = mode.is("Всегда") ? -1 : slot;
            hotbarFrom = -1;
            return;
        }
        if (!need && holding && giveBack.get() && swappedFrom >= 0 && hp > hearts.get() * 2 + 4) {
            if (!p.inventoryMenu.getSlot(swappedFrom).getItem().is(Items.TOTEM_OF_UNDYING)) {
                mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, swappedFrom, 40, ClickType.SWAP, p);
                cooldown = delay.asInt();
            }
            swappedFrom = -1;
        }
        if (!holding && swappedFrom >= 0 && !need) swappedFrom = -1;
    }

    /** Перекладывать в инвентаре без открытого окна можно, только когда игрок спокойно стоит (или это разрешено). */
    private boolean inventoryAllowed(Minecraft mc) {
        return mc.screen instanceof InventoryScreen || invMoving.get() || Human.standingStill(8);
    }

    private void startHotbarSwap(LocalPlayer p, int hb) {
        prevSlot = p.getInventory().getSelectedSlot();
        if (hb != prevSlot) {
            p.getInventory().setSelectedSlot(hb);
            stepWait = tune.react(1, 3);
        } else {
            prevSlot = -1;
            stepWait = 0;
        }
        step = 1;
    }

    private void runStep(Minecraft mc, LocalPlayer p) {
        if (stepWait > 0) {
            stepWait--;
            return;
        }
        if (step == 1) {
            if (mc.screen != null) { // открыли окно - F сейчас не сработает
                step = 2;
                return;
            }
            Keys.click(mc.options.keySwapOffhand);
            step = 2;
            stepWait = tune.react(2, 5);
            return;
        }
        if (prevSlot >= 0 && mc.screen == null) p.getInventory().setSelectedSlot(prevSlot);
        prevSlot = -1;
        step = 0;
        cooldown = Human.ticks(Math.max(2, delay.get() + 2));
    }

    private static int hotbarTotem(LocalPlayer p) {
        int sel = p.getInventory().getSelectedSlot();
        if (p.getInventory().getItem(sel).is(Items.TOTEM_OF_UNDYING)) return sel;
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
        return -1;
    }

    /** Слот меню инвентаря с тотемом в основном инвентаре (9-35). */
    private static int findTotem(LocalPlayer p) {
        for (int i = 9; i <= 35; i++) {
            if (p.inventoryMenu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private void swap(Minecraft mc, LocalPlayer p, int menuSlot) {
        // SWAP с кнопкой 40 - обмен слота со второй рукой (как клавиша F при наведении на слот в инвентаре)
        mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot, 40, ClickType.SWAP, p);
        cooldown = Human.ticks(Math.max(2, delay.get() + 2));
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

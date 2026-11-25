package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;

/**
 * Авто-сфера воздуха (ветряной заряд): бросает заряд строго себе под ноги и подбрасывает тебя вверх.
 * Заряд должен лежать в хотбаре или во второй руке.
 */
public class AutoWindCharge extends Module {
    private final ModeSetting mode = add(new ModeSetting("Режим", "По клавише: включил - один бросок под ноги и функция выключается. При прыжке: бросает заряд каждый раз, когда ты прыгаешь с земли.", "По клавише (один бросок)",
            "По клавише (один бросок)", "При прыжке"));
    private final NumSetting cooldown = add(new NumSetting("Перезарядка (тики)", "Минимальная пауза между бросками. У ветряного заряда своя перезарядка около 10 тиков.", 12, 8, 40, 1));
    private final BoolSetting message = add(new BoolSetting("Сообщать об ошибке", "Писать в чат, если ветряного заряда нет в хотбаре.", true));

    private long tick, last;

    public AutoWindCharge() {
        super("AutoWindCharge", "Авто-сфера воздуха: бросает ветряной заряд себе под ноги и подбрасывает тебя вверх.", Category.MOVEMENT);
    }

    @Override
    public String info() {
        return mode.is("При прыжке") ? "прыжок" : null;
    }

    @Override
    public void onTick(Minecraft mc) {
        tick++;
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || mc.screen != null) return;

        if (mode.is("При прыжке")) {
            if (p.onGround() && mc.options.keyJump.isDown() && tick - last >= cooldown.asInt()) {
                throwUnderFeet(mc, p);
            }
            return;
        }
        // По клавише: один бросок и выключение. Раньше флаг ставился только в onEnable, и функция,
        // включённая из конфига при запуске игры, "висела" включённой и ничего не делала.
        throwUnderFeet(mc, p);
        setEnabled(false);
    }

    private void throwUnderFeet(Minecraft mc, LocalPlayer p) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        Inventory inv = p.getInventory();
        int slot = -1;
        if (p.getOffhandItem().is(Items.WIND_CHARGE)) {
            hand = InteractionHand.OFF_HAND;
        } else {
            for (int i = 0; i < 9; i++) {
                if (inv.getItem(i).is(Items.WIND_CHARGE)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                if (message.get()) p.displayClientMessage(Component.literal("[PepeVisual] Нет ветряного заряда в хотбаре"), true);
                last = tick;
                return;
            }
        }
        int prev = inv.getSelectedSlot();
        float oldX = p.getXRot();
        if (hand == InteractionHand.MAIN_HAND && slot != prev) inv.setSelectedSlot(slot);
        p.setXRot(90f); // строго вниз: бросок идёт в пол под ногами
        mc.gameMode.useItem(p, hand);
        p.swing(hand);
        p.setXRot(oldX);
        if (hand == InteractionHand.MAIN_HAND && slot != prev) inv.setSelectedSlot(prev);
        last = tick;
    }
}

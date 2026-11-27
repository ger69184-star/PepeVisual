package ru.visual.client.module.impl;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.util.Mth;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.util.Keys;

/**
 * Ходьба с открытым инвентарём / сундуком: W A S D, прыжок и бег работают, пока открыто окно с предметами.
 * Не включается там, где печатают текст (чат, таблички, книги, наковальня, поиск в творческом), и в меню клиента.
 */
public class InventoryMove extends Module {
    private final BoolSetting jump = add(new BoolSetting("Прыжок", "Пробел тоже работает в инвентаре.", true));
    private final BoolSetting sprint = add(new BoolSetting("Бег", "Ctrl (клавиша бега) тоже работает.", true));
    private final BoolSetting arrows = add(new BoolSetting("Поворот стрелками", "Стрелки на клавиатуре поворачивают камеру, пока открыт инвентарь.", false));

    private boolean wasActive;

    public InventoryMove() {
        super("InventoryMove", "Можно ходить, прыгать и бегать с открытым инвентарём или сундуком.", Category.MOVEMENT);
    }

    private static boolean allowed(Screen s) {
        if (!(s instanceof AbstractContainerScreen<?>)) return false;
        if (s instanceof AnvilScreen) return false; // поле для имени
        if (s instanceof CreativeModeInventoryScreen c && !c.isInventoryOpen()) return false; // вкладки с поиском
        return !(s.getFocused() instanceof EditBox);
    }

    private static boolean physical(Minecraft mc, KeyMapping km) {
        return Keys.physical(mc, km);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        boolean active = mc.screen != null && allowed(mc.screen);
        if (!active) {
            if (wasActive) release(mc);
            wasActive = false;
            return;
        }
        wasActive = true;
        boolean fwd = physical(mc, mc.options.keyUp);
        mc.options.keyUp.setDown(fwd);
        mc.options.keyDown.setDown(physical(mc, mc.options.keyDown));
        mc.options.keyLeft.setDown(physical(mc, mc.options.keyLeft));
        mc.options.keyRight.setDown(physical(mc, mc.options.keyRight));
        mc.options.keyJump.setDown(jump.get() && physical(mc, mc.options.keyJump));
        // Автоспринт работает сам (LocalPlayerMixin). Клавишу-переключатель бега не трогаем: setDown(true) её переключает.
        if (!mc.options.toggleSprint().get()) mc.options.keySprint.setDown(sprint.get() && physical(mc, mc.options.keySprint));

        if (arrows.get()) {
            float yaw = 0, pitch = 0;
            if (InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LEFT)) yaw -= 5;
            if (InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RIGHT)) yaw += 5;
            if (InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_UP)) pitch -= 4;
            if (InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_DOWN)) pitch += 4;
            if (yaw != 0 || pitch != 0) {
                mc.player.setYRot(mc.player.getYRot() + yaw);
                mc.player.setXRot(Mth.clamp(mc.player.getXRot() + pitch, -90f, 90f));
            }
        }
    }

    private static void release(Minecraft mc) {
        // после закрытия окна игра сама перечитает клавиши; здесь - на случай, если окно сменилось на чат и т.п.
        if (mc.screen == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (wasActive && mc.options != null) release(mc);
        wasActive = false;
    }
}

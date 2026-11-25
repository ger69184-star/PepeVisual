package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;

public class AutoSprint extends Module implements Config.DefaultOn {
    public AutoSprint() {
        super("AutoSprint", "Автоматический спринт: достаточно держать W, без двойного нажатия или Ctrl.", Category.MOVEMENT);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.screen != null) return;
        if (mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown()) {
            mc.options.keySprint.setDown(true);
        }
    }

    @Override
    protected void onDisable() {
        Minecraft.getInstance().options.keySprint.setDown(false);
    }
}

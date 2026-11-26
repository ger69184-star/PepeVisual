package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;

public class AutoWalk extends Module {
    public AutoWalk() {
        super("AutoWalk", "Автоматически идёт вперёд, пока функция включена (клавишу W держать не нужно).", Category.MOVEMENT);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.screen != null) return;
        mc.options.keyUp.setDown(true);
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        ru.visual.client.util.Keys.release(mc, mc.options.keyUp);
    }
}

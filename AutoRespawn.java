package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.NumSetting;

public class AutoRespawn extends Module {
    private final NumSetting delay = add(new NumSetting("Задержка (тики)", "Через сколько тиков после смерти возродиться. 20 тиков = 1 секунда.", 10, 0, 60, 1));
    private int ticks;

    public AutoRespawn() {
        super("AutoRespawn", "Автоматически возрождает после смерти, без нажатия кнопки.", Category.UTILITY);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player != null && mc.screen instanceof DeathScreen) {
            if (++ticks >= delay.asInt()) {
                mc.player.respawn();
                ticks = 0;
            }
        } else {
            ticks = 0;
        }
    }
}

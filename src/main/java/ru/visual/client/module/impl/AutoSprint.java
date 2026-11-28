package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;

/**
 * Автоспринт. Работает через LocalPlayerMixin: "спринт зажат" подставляется прямо в тот тик, когда нажата W,
 * поэтому бег включается сразу, а после удара или упора в стену - снова в следующий же тик.
 * Клавишу Ctrl больше не трогаем, так что с опцией "Переключение бега" ничего не мигает.
 */
public class AutoSprint extends Module implements Config.DefaultOn {
    private static AutoSprint instance;

    private final BoolSetting water = add(new BoolSetting("Плавание", "Плыть быстрым кролем под водой, пока держишь W.", true));
    private final BoolSetting usingItem = add(new BoolSetting("Не мешать при еде", "Пока ешь или натягиваешь лук, спринт не включается (как в обычной игре - иначе он сразу сбрасывается).", true));

    public AutoSprint() {
        super("AutoSprint", "Автоматический спринт: бег включается сразу, как только нажата W - без двойного нажатия и Ctrl.", Category.MOVEMENT);
        instance = this;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    /** Нужно ли считать спринт зажатым при этих клавишах (вызывает миксин в LocalPlayer.aiStep). */
    public static boolean wants(Input k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !k.forward() || k.backward() || k.shift()) return false;
        if (!instance.water.get() && mc.player.isInWater()) return false;
        return !instance.usingItem.get() || !mc.player.isUsingItem();
    }

}

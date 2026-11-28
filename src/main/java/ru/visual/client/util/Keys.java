package ru.visual.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Настоящее состояние клавиши на клавиатуре (а не то, что выставили модули через setDown). */
public final class Keys {
    private Keys() {}

    public static boolean physical(Minecraft mc, KeyMapping km) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(km);
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0) {
            return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
        }
        return false;
    }

    /** Отпустить "нажатую модулем" клавишу, но оставить нажатой, если игрок держит её сам. */
    public static void release(Minecraft mc, KeyMapping km) {
        km.setDown(physical(mc, km));
    }

    /** Одно нажатие клавиши (как будто игрок кликнул): ванильный код в следующем тике сам сделает удар/использование. */
    public static void click(KeyMapping km) {
        ru.visual.client.mixin.KeyMappingAccessor a = (ru.visual.client.mixin.KeyMappingAccessor) km;
        a.pepevisual$setClickCount(a.pepevisual$getClickCount() + 1);
    }
}

package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.util.TargetManager;

/**
 * Свечение (контур сквозь стены) через MinecraftMixin.shouldEntityAppearGlowing.
 * Раньше использовался setGlowingTag - на клиенте он ничего не делал (флаг свечения приходит с сервера).
 */
public class Glow extends Module {
    private static Glow instance;

    private final BoolSetting players = add(new BoolSetting("Игроки", "Подсвечивать игроков.", true));
    private final BoolSetting mobs = add(new BoolSetting("Мобы", "Подсвечивать враждебных мобов.", false));
    private final BoolSetting animals = add(new BoolSetting("Животные", "Подсвечивать мирных существ.", false));
    private final BoolSetting items = add(new BoolSetting("Предметы", "Подсвечивать выпавшие предметы.", false));
    private final ModeSetting colorMode = add(new ModeSetting("Цвет", "Команда - цвет команды на сервере (как в ванилле), Свой - цвет ниже.", "Свой", "Свой", "Команда"));
    private final ColorSetting color = add(new ColorSetting("Цвет контура", "Цвет свечения.", 0x7CFC00));
    private final BoolSetting targetRed = add(new BoolSetting("Цель красным", "Текущая цель (кого ты ударил) светится красным.", true));

    public Glow() {
        super("Glow", "Контур вокруг игроков/мобов/предметов сквозь стены. Видно только тебе.", Category.VISUAL);
        instance = this;
    }

    private boolean matches(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player || e.isInvisible()) return false;
        if (e instanceof Player) return players.get();
        if (e instanceof ItemEntity) return items.get();
        if (e instanceof Enemy) return mobs.get();
        if (e instanceof LivingEntity) return animals.get();
        return false;
    }

    public static boolean shouldGlow(Entity e) {
        Glow g = instance;
        return g != null && g.isEnabled() && g.matches(e);
    }

    /** Цвет контура или null - оставить ванильный. */
    public static Integer colorFor(Entity e) {
        Glow g = instance;
        if (g == null || !g.isEnabled() || !g.matches(e)) return null;
        if (g.targetRed.get() && TargetManager.isTarget(e)) return 0xFF3030;
        if (g.colorMode.is("Команда")) return null;
        return g.color.argb() & 0xFFFFFF;
    }
}

package ru.visual.client.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;

public class Glow extends Module {
    private final BoolSetting onlyPlayers = add(new BoolSetting("Только игроки", "Подсвечивать только игроков. Выкл = все существа.", true));

    public Glow() {
        super("Glow", "Ванильное свечение (контур) вокруг игроков, видно сквозь стены. Работает только у тебя на экране.", Category.VISUAL);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player) continue;
            boolean should = !onlyPlayers.get() || e instanceof Player;
            if (should && !e.isCurrentlyGlowing()) e.setGlowingTag(true);
            else if (!should) e.setGlowingTag(false);
        }
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) e.setGlowingTag(false);
    }
}

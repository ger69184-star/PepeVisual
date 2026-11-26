package ru.visual.client.module.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.setting.BoolSetting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Счётчик тотемов: пишет в чат, когда игрок рядом сработал тотем бессмертия, и сколько тотемов он уже потерял.
 * Счёт сбрасывается, когда игрок умирает, и при смене мира.
 */
public class TotemPops extends Module {
    private final BoolSetting self = add(new BoolSetting("Свои тотемы", "Сообщать и о своих тотемах.", true));
    private final BoolSetting deaths = add(new BoolSetting("Смерть после тотемов", "Писать, сколько тотемов игрок потратил перед смертью.", true));

    private static final Map<UUID, Integer> pops = new HashMap<>();
    private Object lastLevel;

    public TotemPops() {
        super("TotemPops", "Пишет в чат, когда у игрока рядом сработал тотем, и сколько тотемов он уже потерял.", Category.UTILITY);
    }

    /** Вызывается из ClientPacketListenerMixin (событие 35 - сработал тотем). */
    public static void onPop(Entity e) {
        TotemPops m = ModuleManager.totemPops;
        if (m == null || !m.isEnabled() || !(e instanceof Player p)) return;
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && !m.self.get()) return;
        int n = pops.merge(p.getUUID(), 1, Integer::sum);
        String who = p == mc.player ? "Ты" : p.getName().getString();
        m.say(Component.literal(who).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" потерял тотем ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("(" + n + ")").withStyle(n >= 3 ? ChatFormatting.RED : ChatFormatting.GOLD)));
    }

    private void say(Component c) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.getChat().addMessage(Component.literal("[PV] ").withStyle(ChatFormatting.GREEN).append(c));
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level != lastLevel) {
            pops.clear();
            lastLevel = mc.level;
        }
        if (pops.isEmpty() || mc.level == null) return;
        for (Player p : mc.level.players()) {
            if (!p.isDeadOrDying()) continue;
            Integer n = pops.remove(p.getUUID());
            if (n != null && deaths.get()) {
                say(Component.literal(p == mc.player ? "Ты" : p.getName().getString()).withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(" умер, потратив тотемов: " + n).withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    @Override
    protected void onDisable() {
        pops.clear();
    }
}

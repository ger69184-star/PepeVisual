package ru.visual.client.module.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.setting.BoolSetting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Время [12:34] перед каждым сообщением в чате. */
public class ChatTimestamps extends Module {
    private final BoolSetting seconds = add(new BoolSetting("Секунды", "Показывать и секунды: [12:34:56].", false));

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ChatTimestamps() {
        super("ChatTimestamps", "Время перед каждым сообщением в чате - видно, когда что написали.", Category.UTILITY);
    }

    public static Component decorate(Component msg) {
        ChatTimestamps m = ModuleManager.chatTimestamps;
        if (m == null || !m.isEnabled() || msg == null) return msg;
        String t = LocalTime.now().format(m.seconds.get() ? HMS : HM);
        return Component.literal("[" + t + "] ").withStyle(ChatFormatting.DARK_GRAY).append(msg);
    }
}

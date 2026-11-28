package ru.visual.client.module.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ModeSetting;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Подсветка сообщений в чате: координаты, упоминание твоего никнейма и ключевые слова.
 * Работает через ChatComponentMixin - декорирует сообщение перед отрисовкой (как ChatTimestamps).
 */
public class ChatEsp extends Module {
    private final BoolSetting coords = add(new BoolSetting("Координаты", "Подсвечивать сообщения с координатами (три числа: X Y Z).", true));
    private final BoolSetting mentions = add(new BoolSetting("Упоминания", "Подсвечивать сообщения, где упоминают тебя.", true));
    private final BoolSetting keywords = add(new BoolSetting("Ключевые слова", "Подсвечивать сообщения с важными словами (база, данж, спавн).", true));
    private final ModeSetting color = add(new ModeSetting("Цвет", "Цвет подсветки подсказанного сообщения.", "Золотой",
            "Золотой", "Красный", "Зелёный", "Голубой"));

    /** Три числа через пробел/запятую: 12 34 56, 12,34,56, -12 34.5 67. */
    private static final Pattern COORDS = Pattern.compile("-?\\d+(?:[.,]\\d+)?\\s*[, ]\\s*-?\\d+(?:[.,]\\d+)?\\s*[, ]\\s*-?\\d+(?:[.,]\\d+)?");
    private static final String[] WORDS = {"база", "данж", "спавн"};

    public ChatEsp() {
        super("ChatESP", "Подсветка важных сообщений в чате: координаты, упоминания и ключевые слова.", Category.UTILITY);
    }

    private static ChatFormatting style() {
        ChatEsp m = ModuleManager.chatEsp;
        if (m == null || !m.isEnabled()) return null;
        return switch (m.color.get()) {
            case "Красный" -> ChatFormatting.RED;
            case "Зелёный" -> ChatFormatting.GREEN;
            case "Голубой" -> ChatFormatting.DARK_AQUA;
            default -> ChatFormatting.GOLD;
        };
    }

    /** true - сообщение подпадает под выбранные фильтры. */
    private boolean matches(Component msg) {
        if (msg == null) return false;
        String text = msg.getString().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return false;
        if (coords.get() && COORDS.matcher(text).find()) return true;
        if (mentions.get()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String nick = mc.player.getName().getString().toLowerCase(Locale.ROOT);
                if (!nick.isEmpty() && text.contains(nick)) return true;
            }
        }
        if (keywords.get()) {
            for (String w : WORDS) if (text.contains(w)) return true;
        }
        return false;
    }

    /** Декорирует сообщение перед отрисовкой: добавляет colored-маркер спереди. */
    public static Component decorate(Component msg) {
        ChatFormatting st = style();
        if (st == null) return msg;
        ChatEsp m = ModuleManager.chatEsp;
        if (m == null || !m.matches(msg)) return msg;
        return Component.literal("» ").withStyle(st).append(msg);
    }
}

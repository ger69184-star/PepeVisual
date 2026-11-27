package ru.visual.client.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ru.visual.client.VisualClient;
import ru.visual.client.setting.ModeSetting;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Язык клиента (Русский / English). Тексты в коде написаны по-русски, английский перевод лежит в
 * assets/pepevisual/texts/en.json: ключ - русская строка, значение - английская.
 * Внутренние значения настроек (режимы в конфиге) всегда русские - переводится только то, что видно на экране,
 * поэтому смена языка не сбрасывает настройки.
 */
public final class Lang {
    /** Настройка "Язык" из модуля Menu. */
    public static ModeSetting language;

    private static Map<String, String> en;

    private Lang() {}

    public static boolean en() {
        return language != null && language.is("English");
    }

    /** Перевод строки на текущий язык (если перевода нет - как есть). */
    public static String t(String ru) {
        if (ru == null || !en()) return ru;
        String v = map().get(ru);
        return v != null ? v : ru;
    }

    /** String.format с переводом шаблона: Lang.f("Крипер %.1f с", t). */
    public static String f(String ru, Object... args) {
        return String.format(Locale.ROOT, t(ru), args);
    }

    /** Выбор строки прямо в коде: Lang.of("Привет", "Hello"). */
    public static String of(String ru, String english) {
        return en() ? english : ru;
    }

    private static Map<String, String> map() {
        if (en != null) return en;
        Map<String, String> out = new HashMap<>();
        try (InputStream in = Lang.class.getResourceAsStream("/assets/pepevisual/texts/en.json")) {
            if (in != null) {
                JsonObject o = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
                for (Map.Entry<String, JsonElement> e : o.entrySet()) out.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            VisualClient.LOGGER.error("Не удалось загрузить английский перевод", e);
        }
        en = out;
        return en;
    }
}

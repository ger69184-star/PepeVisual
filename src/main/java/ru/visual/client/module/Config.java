package ru.visual.client.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import ru.visual.client.VisualClient;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.ColorSetting;
import ru.visual.client.setting.ModeSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.setting.Setting;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

/** Сохранение состояния модулей в config/pepevisual.json */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("pepevisual.json");
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            for (Module m : ModuleManager.MODULES) {
                JsonObject mo = new JsonObject();
                mo.addProperty("enabled", m.isEnabled());
                JsonObject so = new JsonObject();
                for (Setting<?> s : m.settings) {
                    if (s instanceof BoolSetting b) so.addProperty(s.name, b.get());
                    else if (s instanceof NumSetting n) so.addProperty(s.name, n.get());
                    else if (s instanceof ModeSetting md) so.addProperty(s.name, md.get());
                    else if (s instanceof ColorSetting c) {
                        JsonObject co = new JsonObject();
                        co.addProperty("hue", c.hue);
                        co.addProperty("sat", c.sat);
                        co.addProperty("bri", c.bri);
                        co.addProperty("rainbow", c.rainbow);
                        so.add(s.name, co);
                    }
                }
                mo.add("settings", so);
                root.add(m.name, mo);
            }
            writeAtomic(file(), root);
        } catch (Exception e) {
            VisualClient.LOGGER.error("Не удалось сохранить конфиг", e);
        }
    }

    public static void load() {
        Path f = file();
        JsonObject root = null;
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                root = GSON.fromJson(r, JsonObject.class); // пустой файл -> null -> настройки по умолчанию
            } catch (Exception e) {
                VisualClient.LOGGER.error("Не удалось прочитать конфиг - используются настройки по умолчанию", e);
                // испорченный файл не затираем молча: копия останется рядом
                try {
                    Files.copy(f, f.resolveSibling(f.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }
        for (Module m : ModuleManager.MODULES) {
            boolean enable = m.defaultEnabled();
            if (root != null && root.has(m.name) && root.get(m.name).isJsonObject()) {
                JsonObject mo = root.getAsJsonObject(m.name);
                try {
                    if (mo.has("enabled")) enable = mo.get("enabled").getAsBoolean();
                } catch (Exception ignored) {} // "enabled": "abc" / {} - раньше игра падала при запуске
                if (mo.has("settings") && mo.get("settings").isJsonObject()) {
                    JsonObject so = mo.getAsJsonObject("settings");
                    for (Setting<?> s : m.settings) {
                        if (!so.has(s.name)) continue;
                        try {
                            if (s instanceof BoolSetting b) b.set(so.get(s.name).getAsBoolean());
                            else if (s instanceof NumSetting n) n.set(so.get(s.name).getAsDouble());
                            else if (s instanceof ModeSetting md) md.set(so.get(s.name).getAsString());
                            else if (s instanceof ColorSetting c) {
                                JsonObject co = so.getAsJsonObject(s.name);
                                // hsvToRgb бросает исключение при оттенке вне 0..1 - модуль падал бы каждый кадр
                                c.hue = unit(co.get("hue").getAsFloat(), c.hue);
                                c.sat = unit(co.get("sat").getAsFloat(), c.sat);
                                c.bri = unit(co.get("bri").getAsFloat(), c.bri);
                                c.rainbow = co.has("rainbow") && co.get("rainbow").getAsBoolean();
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (enable) m.setEnabledSilently(true);
        }
    }

    /**
     * Запись JSON сначала во временный файл, потом заменой: если игра вылетит посреди записи,
     * старый файл останется целым (раньше мог остаться обрезанный файл и все настройки сбрасывались).
     */
    public static void writeAtomic(Path f, com.google.gson.JsonElement json) throws java.io.IOException {
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(json, w);
        }
        try {
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Значение 0..1 из конфига; NaN/бесконечность - оставить прежнее. */
    private static float unit(float v, float fallback) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return fallback;
        return Math.max(0f, Math.min(1f, v));
    }

    /** Маркер: модуль включён по умолчанию при первом запуске. */
    public interface DefaultOn {}
}

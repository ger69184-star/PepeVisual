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
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            VisualClient.LOGGER.error("Не удалось сохранить конфиг", e);
        }
    }

    public static void load() {
        Path f = file();
        JsonObject root = null;
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f)) {
                root = GSON.fromJson(r, JsonObject.class);
            } catch (Exception e) {
                VisualClient.LOGGER.error("Не удалось прочитать конфиг", e);
            }
        }
        for (Module m : ModuleManager.MODULES) {
            boolean enable = m.defaultEnabled();
            if (root != null && root.has(m.name) && root.get(m.name).isJsonObject()) {
                JsonObject mo = root.getAsJsonObject(m.name);
                if (mo.has("enabled")) enable = mo.get("enabled").getAsBoolean();
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
                                c.hue = co.get("hue").getAsFloat();
                                c.sat = co.get("sat").getAsFloat();
                                c.bri = co.get("bri").getAsFloat();
                                c.rainbow = co.get("rainbow").getAsBoolean();
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (enable) m.setEnabledSilently(true);
        }
    }

    /** Маркер: модуль включён по умолчанию при первом запуске. */
    public interface DefaultOn {}
}

package ru.visual.client.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import ru.visual.client.VisualClient;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

/** Список оффлайн-аккаунтов (ники) и смена текущей сессии. Работает на серверах с offline-mode. */
public final class AccountManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final List<String> NAMES = new ArrayList<>();
    private static String current;
    private static boolean loaded;

    private AccountManager() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("pepevisual_accounts.json");
    }

    public static synchronized List<String> names() {
        load();
        return NAMES;
    }

    public static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    public static synchronized boolean add(String name) {
        load();
        if (!isValid(name) || NAMES.contains(name)) return false;
        NAMES.add(name);
        save();
        return true;
    }

    public static synchronized void remove(String name) {
        load();
        NAMES.remove(name);
        save();
    }

    public static String currentName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() != null ? mc.getUser().getName() : "?";
    }

    public static String random() {
        String[] a = {"Dark", "Swift", "Ice", "Fire", "Neo", "Pixel", "Frost", "Toxic", "Shadow", "Cool"};
        String[] b = {"Wolf", "Fox", "Blade", "Rider", "Craft", "Hunter", "Ghost", "Storm", "Miner", "Knight"};
        Random r = new Random();
        String n = a[r.nextInt(a.length)] + b[r.nextInt(b.length)] + r.nextInt(100);
        return n.length() > 16 ? n.substring(0, 16) : n;
    }

    /** Меняет ник текущей игровой сессии на оффлайн. */
    public static boolean login(String name) {
        if (!isValid(name)) return false;
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        try {
            User user = createUser(name, uuid);
            boolean done = false;
            for (Field f : Minecraft.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() == User.class) {
                    f.setAccessible(true);
                    f.set(mc, user);
                    done = true;
                } else if (f.getType() == GameProfile.class) {
                    f.setAccessible(true);
                    f.set(mc, new GameProfile(uuid, name));
                }
            }
            if (done) {
                current = name;
                save();
            }
            return done;
        } catch (Throwable t) {
            VisualClient.LOGGER.error("Не удалось сменить аккаунт", t);
            return false;
        }
    }

    /** Конструктор User меняется между версиями, поэтому подбираем аргументы по типам. */
    private static User createUser(String name, UUID uuid) throws Exception {
        for (Constructor<?> c : User.class.getDeclaredConstructors()) {
            Class<?>[] types = c.getParameterTypes();
            Object[] args = new Object[types.length];
            int strings = 0;
            boolean ok = true;
            for (int i = 0; i < types.length; i++) {
                Class<?> t = types[i];
                if (t == String.class) args[i] = (strings++ == 0) ? name : "0";
                else if (t == UUID.class) args[i] = uuid;
                else if (t == Optional.class) args[i] = Optional.empty();
                else if (t.isEnum()) {
                    Object pick = t.getEnumConstants()[0];
                    for (Object e : t.getEnumConstants()) if (e.toString().equalsIgnoreCase("legacy")) pick = e;
                    args[i] = pick;
                } else {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            c.setAccessible(true);
            return (User) c.newInstance(args);
        }
        throw new IllegalStateException("Не найден подходящий конструктор net.minecraft.client.User");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            JsonObject o = GSON.fromJson(r, JsonObject.class);
            if (o != null && o.has("accounts")) {
                for (var e : o.getAsJsonArray("accounts")) NAMES.add(e.getAsString());
            }
            if (o != null && o.has("current")) current = o.get("current").getAsString();
        } catch (Exception e) {
            VisualClient.LOGGER.error("Не удалось прочитать аккаунты", e);
        }
    }

    private static void save() {
        try {
            JsonObject o = new JsonObject();
            JsonArray a = new JsonArray();
            for (String n : NAMES) a.add(n);
            o.add("accounts", a);
            if (current != null) o.addProperty("current", current);
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(o, w);
            }
        } catch (Exception e) {
            VisualClient.LOGGER.error("Не удалось сохранить аккаунты", e);
        }
    }
}

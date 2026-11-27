package ru.visual.client.module.impl;

import ru.visual.client.util.Lang;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import ru.visual.client.VisualClient;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;
import ru.visual.client.util.Projection;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Метки (вейпоинты) на экране. Отдельно для каждого сервера/мира и измерения.
 * Команды: /pvwp add <имя>, /pvwp del <имя>, /pvwp list, /pvwp clear.
 */
public class Waypoints extends Module implements Config.DefaultOn {
    public record Point(String name, double x, double y, double z, int color) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Waypoints instance;
    /** ключ мира -> метки */
    private static final Map<String, List<Point>> DATA = new LinkedHashMap<>();
    private static boolean loaded;

    private final BoolSetting distance = add(new BoolSetting("Дистанция", "Показывать расстояние до метки.", true));
    private final BoolSetting coords = add(new BoolSetting("Координаты", "Показывать координаты метки.", false));
    private final NumSetting maxDist = add(new NumSetting("Макс. дистанция", "Дальше этого расстояния метки не рисуются (0 = без ограничения).", 0, 0, 10000, 100));

    public Waypoints() {
        super("Waypoints", "Метки на экране: /pvwp add <имя>, /pvwp del <имя>, /pvwp list. Точка смерти ставится автоматически (Death Info).", Category.UTILITY);
        instance = this;
    }

    // ---------- данные ----------

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("pepevisual_waypoints.json");
    }

    /** Ключ: сервер (или имя одиночного мира) + измерение. */
    public static String worldKey(Minecraft mc) {
        String world;
        ServerData sd = mc.getCurrentServer();
        if (sd != null) world = sd.ip;
        else if (mc.getSingleplayerServer() != null) world = "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        else world = "unknown";
        String dim = mc.level != null ? mc.level.dimension().identifier().toString() : "?";
        return world + "|" + dim;
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(file())) return;
        try (Reader r = Files.newBufferedReader(file())) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;
            for (String key : root.keySet()) {
                List<Point> list = new ArrayList<>();
                for (var el : root.getAsJsonArray(key)) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new Point(o.get("name").getAsString(), o.get("x").getAsDouble(), o.get("y").getAsDouble(),
                            o.get("z").getAsDouble(), o.has("color") ? o.get("color").getAsInt() : 0xFF55FFFF));
                }
                DATA.put(key, list);
            }
        } catch (Exception e) {
            VisualClient.LOGGER.error("Не удалось прочитать метки", e);
        }
    }

    private static synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            for (var e : DATA.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                JsonArray arr = new JsonArray();
                for (Point p : e.getValue()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", p.name());
                    o.addProperty("x", p.x());
                    o.addProperty("y", p.y());
                    o.addProperty("z", p.z());
                    o.addProperty("color", p.color());
                    arr.add(o);
                }
                root.add(e.getKey(), arr);
            }
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            VisualClient.LOGGER.error("Не удалось сохранить метки", e);
        }
    }

    public static synchronized List<Point> current(Minecraft mc) {
        load();
        return DATA.computeIfAbsent(worldKey(mc), k -> new ArrayList<>());
    }

    /** Добавить/заменить метку с таким именем в текущем мире. */
    public static synchronized void put(Minecraft mc, String name, Vec3 pos, int color) {
        List<Point> list = current(mc);
        list.removeIf(p -> p.name().equalsIgnoreCase(name));
        list.add(new Point(name, pos.x, pos.y, pos.z, color));
        save();
    }

    public static synchronized boolean remove(Minecraft mc, String name) {
        boolean r = current(mc).removeIf(p -> p.name().equalsIgnoreCase(name));
        if (r) save();
        return r;
    }

    // ---------- команды ----------

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(ClientCommandManager.literal("pvwp")
                .executes(c -> list(c.getSource()))
                .then(ClientCommandManager.literal("list").executes(c -> list(c.getSource())))
                .then(ClientCommandManager.literal("clear").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    synchronized (Waypoints.class) {
                        current(mc).clear();
                        save();
                    }
                    c.getSource().sendFeedback(Component.literal(Lang.t("§a[PV] Все метки этого мира удалены.")));
                    return 1;
                }))
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(c -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            String name = StringArgumentType.getString(c, "name");
                            Vec3 p = mc.player.position();
                            put(mc, name, new Vec3(Math.floor(p.x) + 0.5, Math.floor(p.y), Math.floor(p.z) + 0.5), 0xFF55FFFF);
                            c.getSource().sendFeedback(Component.literal(String.format(Locale.ROOT,
                                    Lang.t("§a[PV] Метка \"%s\" поставлена: %d %d %d"), name, (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z))));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("del")
                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            boolean ok = remove(Minecraft.getInstance(), name);
                            c.getSource().sendFeedback(Component.literal(ok ? Lang.t("§a[PV] Метка \"") + name + Lang.t("\" удалена.") : Lang.t("§c[PV] Метка \"") + name + Lang.t("\" не найдена.")));
                            return ok ? 1 : 0;
                        }))));
    }

    private static int list(FabricClientCommandSource src) {
        List<Point> list = current(Minecraft.getInstance());
        if (list.isEmpty()) {
            src.sendFeedback(Component.literal(Lang.t("§7[PV] Меток нет. Добавить: /pvwp add <имя>")));
            return 1;
        }
        src.sendFeedback(Component.literal(Lang.t("§b[PV] Метки (") + list.size() + "):"));
        for (Point p : list) {
            src.sendFeedback(Component.literal(String.format(Locale.ROOT, "§7- §f%s§7: %d %d %d", p.name(), (int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()))));
        }
        return 1;
    }

    // ---------- отрисовка ----------

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        List<Point> list;
        synchronized (Waypoints.class) {
            list = new ArrayList<>(current(mc));
        }
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        Vec3 eye = Projection.eye();
        double[] o = new double[3];
        for (Point p : list) {
            double dist = eye.distanceTo(new Vec3(p.x(), p.y(), p.z()));
            if (maxDist.get() > 0 && dist > maxDist.get()) continue;
            // Далёкие метки "подтягиваем" к камере, чтобы проекция оставалась точной
            double tx = p.x(), ty = p.y() + 1.0, tz = p.z();
            if (dist > 200) {
                double k = 200 / dist;
                tx = eye.x + (tx - eye.x) * k;
                ty = eye.y + (ty - eye.y) * k;
                tz = eye.z + (tz - eye.z) * k;
            }
            if (!Projection.project(tx, ty, tz, o)) continue;
            int x = (int) o[0], y = (int) o[1];
            if (x < -100 || x > w + 100 || y < -100 || y > h + 100) continue;
            String label = p.name();
            if (distance.get()) label += " §7" + (int) dist + Lang.t("м");
            String sub = coords.get() ? String.format(Locale.ROOT, "%d %d %d", (int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z())) : null;
            int tw = Draw.width(label);
            Draw.rect(g, x - tw / 2 - 3, y - 12, x + tw / 2 + 3, y - 1, 0xA0000000);
            Draw.rect(g, x - tw / 2 - 3, y - 2, x + tw / 2 + 3, y - 1, p.color() | 0xFF000000);
            Draw.text(g, label, x - tw / 2, y - 11, 0xFFFFFFFF);
            if (sub != null) Draw.textCentered(g, sub, x, y + 2, 0xFFBBBBBB);
            Draw.dot(g, x, y + (sub != null ? 13 : 3), 3, p.color() | 0xFF000000);
        }
    }
}

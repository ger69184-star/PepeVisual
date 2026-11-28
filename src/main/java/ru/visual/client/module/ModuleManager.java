package ru.visual.client.module;

import ru.visual.client.util.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;
import ru.visual.client.module.impl.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModuleManager {
    public static final List<Module> MODULES = new ArrayList<>();

    // Нужны миксинам и обработчикам HUD
    public static NoRender noRender;
    public static HitAnimations hitAnimations;
    public static CustomCrosshair crosshair;
    public static PepeBackground pepe;
    public static TotemPops totemPops;
    public static ChatTimestamps chatTimestamps;

    private static long frame;
    private static boolean configDirty;
    private static int saveTimer;
    /** Мир, в котором были на прошлом тике (по ссылке: на FunTime сервер/лобби меняется без смены измерения). */
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    /** Сколько раз модуль уже писал ошибку в лог (чтобы ошибка каждый кадр не забивала лог гигабайтами). */
    private static final java.util.Map<Module, Integer> ERRORS = new java.util.HashMap<>();

    private static void error(Module m, String where, Throwable t) {
        int n = ERRORS.merge(m, 1, Integer::sum);
        if (n <= 3) {
            ru.visual.client.VisualClient.LOGGER.error("Ошибка в модуле {} ({})", m.name, where, t);
        } else if (n == 4) {
            ru.visual.client.VisualClient.LOGGER.error("Модуль {} продолжает падать ({}), дальнейшие ошибки не пишутся", m.name, where);
        }
    }

    private ModuleManager() {}

    /** Номер кадра HUD (для редактора HUD и анимаций). */
    public static long frame() {
        return frame;
    }

    /** Модуль включили/выключили: конфиг сохранится через пару секунд (не теряется при вылете игры). */
    static void onToggled(Module m) {
        configDirty = true;
        saveTimer = 0;
        // Уведомление только при включении клавишей в игре: в меню и так видно, что нажал
        if (Minecraft.getInstance().screen == null) Notifications.onToggle(m);
    }

    /** Отметить, что настройки изменились (меню, редактор HUD). */
    public static void markDirty() {
        configDirty = true;
        saveTimer = 0;
    }

    public static void init() {
        // ---- Visual ----
        MODULES.add(new PlayerEsp());
        MODULES.add(new TargetEsp());
        MODULES.add(new Trajectories());
        MODULES.add(new ItemEsp());
        MODULES.add(new Glow());
        MODULES.add(new JumpCircles());
        MODULES.add(new DamageIndicators());
        MODULES.add(new HitParticles());
        hitAnimations = new HitAnimations();
        MODULES.add(hitAnimations);
        crosshair = new CustomCrosshair();
        MODULES.add(crosshair);
        MODULES.add(new Fullbright());
        MODULES.add(new Zoom());
        MODULES.add(new BlockOutline());
        MODULES.add(new CustomFog());
        MODULES.add(new WorldTime());
        MODULES.add(new StorageEsp());
        MODULES.add(new ShulkerPreview());
        MODULES.add(new ViewModel());
        MODULES.add(new Arrows());
        MODULES.add(new SpawnHighlight());
        MODULES.add(new Explosions());
        MODULES.add(new Breadcrumbs());
        MODULES.add(new AimAlert());
        MODULES.add(new KillEffects());

        // ---- HUD ----
        pepe = new PepeBackground();
        MODULES.add(pepe);
        MODULES.add(new Watermark());
        MODULES.add(new TargetHud());
        MODULES.add(new HotbarView());
        MODULES.add(new ModuleList());
        MODULES.add(new InfoHud());
        MODULES.add(new ArmorHud());
        MODULES.add(new PotionHud());
        MODULES.add(new ItemCounter());
        MODULES.add(new Cooldowns());
        MODULES.add(new NearbyPlayers());
        MODULES.add(new Radar());
        MODULES.add(new Keystrokes());
        MODULES.add(new ServerInfo());
        MODULES.add(new HungerHud());
        MODULES.add(new DurabilityWarning());
        MODULES.add(new LowHealthWarning());
        MODULES.add(new Notifications());
        MODULES.add(new Compass());
        MODULES.add(new InventoryHud());
        MODULES.add(new SessionStats());
        MODULES.add(new ComboHud());

        // ---- NoRender ----
        noRender = new NoRender();
        MODULES.add(noRender);

        // ---- Helpers ----
        MODULES.add(new AutoBrew());
        MODULES.add(new BuildHelper());
        MODULES.add(new ChestSorter());
        MODULES.add(new AutoFarm());
        MODULES.add(new DungeonFinder());
        MODULES.add(new BaseFinder());
        MODULES.add(new AutoTool());
        MODULES.add(new AutoEat());
        MODULES.add(new AutoTotem());
        MODULES.add(new AutoArmor());

        // ---- Movement ----
        MODULES.add(new AutoSprint());
        MODULES.add(new AutoWalk());
        MODULES.add(new InventoryMove());
        MODULES.add(new FreeLook());
        MODULES.add(new AutoWindCharge());
        MODULES.add(new AutoPearl());

        // ---- Utility / Optimization ----
        MODULES.add(new AutoRespawn());
        MODULES.add(new Waypoints());
        MODULES.add(new DeathInfo());
        MODULES.add(new LogoutSpots());
        MODULES.add(new AntiAfk());

        totemPops = new TotemPops();
        MODULES.add(totemPops);
        chatTimestamps = new ChatTimestamps();
        MODULES.add(chatTimestamps);

        // ---- Optimization: каждая настройка - отдельная кнопка ----
        MODULES.addAll(GraphicsTweak.createAll());

        // ---- Menu: оформление меню, тема, язык ----
        MODULES.add(new MenuSettings());
        MODULES.add(new Interface());

        // Клавиша включения для каждой функции (меняется в меню клиента)
        for (Module m : MODULES) {
            String id = m.name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            m.bind = KeyBindingHelper.registerKeyBinding(
                    new KeyMapping("key.pepevisual.bind." + id, GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.MISC));
        }

        Config.load();
    }

    /** Назначает клавишу функции (keyCode < 0 - сбросить). */
    public static void setBind(Module m, int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        if (m.bind == null) return;
        InputConstants.Key key = keyCode < 0 ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(keyCode);
        m.bind.setKey(key);
        KeyMapping.resetMapping();
        if (mc.options != null) mc.options.save();
    }

    public static String bindName(Module m) {
        if (m.bind == null || m.bind.isUnbound()) return Lang.t("нет");
        return m.bind.getTranslatedKeyMessage().getString();
    }

    public static List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) if (m.category == c) out.add(m);
        return out;
    }

    public static void tick(Minecraft mc) {
        for (Module m : MODULES) {
            if (m.bind != null) {
                while (m.bind.consumeClick()) {
                    if (mc.screen == null) m.toggle();
                }
            }
        }
        if (configDirty && ++saveTimer >= 40) {
            configDirty = false;
            Config.save();
        }
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            worldChanged(mc);
        }
        boolean inWorld = mc.player != null && mc.level != null;
        for (Module m : MODULES) {
            if (m.isEnabled() && (inWorld || m.tickOutsideWorld())) {
                try {
                    m.onTick(mc);
                } catch (Throwable t) {
                    error(m, "tick", t);
                }
            }
        }
    }

    /** Мир сменился (или из него вышли): все модули и общие помощники забывают старый мир. */
    private static void worldChanged(Minecraft mc) {
        ru.visual.client.util.Aim.releaseAll();
        ru.visual.client.util.TargetManager.reset();
        ru.visual.client.util.ContainerMemory.reset();
        for (Module m : MODULES) {
            try {
                m.onWorldChange(mc);
            } catch (Throwable t) {
                error(m, "world change", t);
            }
        }
    }

    public static void onAttack(Entity target) {
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.onAttack(target);
            } catch (Throwable t) {
                error(m, "attack", t);
            }
        }
    }

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        frame++;
        if (mc.player == null || mc.level == null) return;
        ru.visual.client.util.Projection.update(mc, delta.getGameTimeDeltaPartialTick(false));
        HudModule.beginFrame();
        for (Module m : MODULES) {
            if (m.isEnabled()) {
                ru.visual.client.util.Draw.tracking = m instanceof HudModule hm ? hm : null;
                try {
                    m.onHud(g, delta, mc);
                } catch (Throwable t) {
                    error(m, "hud", t);
                }
                ru.visual.client.util.Draw.tracking = null;
                if (m instanceof HudModule hm) hm.afterHud();
            }
        }
    }

    public static void renderWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ru.visual.client.util.R3D.begin(ctx);
        try {
            for (Module m : MODULES) {
                if (!m.isEnabled()) continue;
                try {
                    m.onWorld(ctx, pt, mc);
                } catch (Throwable t) {
                    error(m, "world", t);
                }
            }
        } finally {
            ru.visual.client.util.R3D.end();
        }
    }

    public static void shutdown() {
        for (Module m : MODULES) {
            try {
                if (m.isEnabled()) m.onShutdown();
            } catch (Throwable ignored) {}
        }
        Config.save();
        // настройки графики уже возвращены кнопками оптимизации - записываем исходные значения в options.txt
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) mc.options.save();
    }
}

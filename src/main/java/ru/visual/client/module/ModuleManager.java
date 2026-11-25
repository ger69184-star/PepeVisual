package ru.visual.client.module;

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
    public static Optimization optimization;
    public static HitAnimations hitAnimations;
    public static CustomCrosshair crosshair;
    public static PepeBackground pepe;

    private static long frame;
    private static boolean configDirty;
    private static int saveTimer;

    private ModuleManager() {}

    /** Номер кадра HUD (для редактора HUD и анимаций). */
    public static long frame() {
        return frame;
    }

    /** Модуль включили/выключили: конфиг сохранится через пару секунд (не теряется при вылете игры). */
    static void onToggled(Module m) {
        configDirty = true;
        saveTimer = 0;
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
        MODULES.add(new Tracers());
        MODULES.add(new Glow());
        MODULES.add(new JumpCircles());
        MODULES.add(new Trails());
        MODULES.add(new DamageIndicators());
        MODULES.add(new HitParticles());
        MODULES.add(new HitsToKill());
        hitAnimations = new HitAnimations();
        MODULES.add(hitAnimations);
        crosshair = new CustomCrosshair();
        MODULES.add(crosshair);
        MODULES.add(new Fullbright());
        MODULES.add(new Zoom());
        MODULES.add(new BlockOutline());
        MODULES.add(new CustomFog());
        MODULES.add(new WorldTime());

        // ---- HUD ----
        pepe = new PepeBackground();
        MODULES.add(pepe);
        MODULES.add(new Interface());
        MODULES.add(new Watermark());
        MODULES.add(new TargetHud());
        MODULES.add(new ModuleList());
        MODULES.add(new InfoHud());
        MODULES.add(new ArmorHud());
        MODULES.add(new PotionHud());
        MODULES.add(new ItemCounter());
        MODULES.add(new Cooldowns());
        MODULES.add(new NearbyPlayers());
        MODULES.add(new Radar());
        MODULES.add(new Keystrokes());

        // ---- NoRender ----
        noRender = new NoRender();
        MODULES.add(noRender);

        // ---- Helpers ----
        MODULES.add(new AutoBrew());
        MODULES.add(new BuildHelper());
        MODULES.add(new AutoFarm());
        MODULES.add(new DungeonFinder());
        MODULES.add(new BaseFinder());

        // ---- Movement ----
        MODULES.add(new AutoSprint());
        MODULES.add(new AutoWalk());
        MODULES.add(new FreeLook());
        MODULES.add(new AutoWindCharge());

        // ---- Utility / Optimization ----
        MODULES.add(new AutoRespawn());
        optimization = new Optimization();
        MODULES.add(optimization);

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
        if (m.bind == null || m.bind.isUnbound()) return "не назначена";
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
        for (Module m : MODULES) {
            if (m.isEnabled()) {
                try {
                    m.onTick(mc);
                } catch (Throwable t) {
                    ru.visual.client.VisualClient.LOGGER.error("Ошибка в модуле {} (tick)", m.name, t);
                }
            }
        }
    }

    public static void onAttack(Entity target) {
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.onAttack(target);
            } catch (Throwable t) {
                ru.visual.client.VisualClient.LOGGER.error("Ошибка в модуле {} (attack)", m.name, t);
            }
        }
    }

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        frame++;
        if (mc.player == null || mc.level == null) return;
        ru.visual.client.util.Projection.update(mc, delta.getGameTimeDeltaPartialTick(false));
        for (Module m : MODULES) {
            if (m.isEnabled()) {
                try {
                    m.onHud(g, delta, mc);
                } catch (Throwable t) {
                    ru.visual.client.VisualClient.LOGGER.error("Ошибка в модуле {} (hud)", m.name, t);
                }
            }
        }
    }

    public static void shutdown() {
        for (Module m : MODULES) {
            try {
                if (m.isEnabled()) m.onShutdown();
            } catch (Throwable ignored) {}
        }
        Config.save();
    }
}

package ru.visual.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.visual.client.gui.ClickGuiScreen;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.module.impl.NoRender;
import ru.visual.client.module.impl.PepeBackground;
import ru.visual.client.util.TargetManager;

public class VisualClient implements ClientModInitializer {
    public static final String MOD_ID = "pepevisual";
    public static final Logger LOGGER = LoggerFactory.getLogger("PepeVisual");

    public static KeyMapping guiKey;
    public static KeyMapping zoomKey;
    public static KeyMapping freeLookKey;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();

        guiKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pepevisual.gui", GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));
        zoomKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pepevisual.zoom", GLFW.GLFW_KEY_C, KeyMapping.Category.MISC));

        freeLookKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pepevisual.freelook", GLFW.GLFW_KEY_LEFT_ALT, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (guiKey.consumeClick()) {
                mc.setScreen(new ClickGuiScreen());
            }
            TargetManager.tick(mc);
            ModuleManager.tick(mc);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ModuleManager.shutdown());

        // Лягушонок на заднем плане: рисуется раньше всех элементов HUD
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(MOD_ID, "pepe_background"),
                PepeBackground::renderBackground);

        // Наш HUD / ESP (рисуется поверх ванильного HUD)
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.SUBTITLES,
                Identifier.fromNamespaceAndPath(MOD_ID, "modules"),
                ModuleManager::renderHud);

        // NoRender: скрытие ванильных элементов экрана
        NoRender.registerHudReplacements();

        // Цель для Target HUD / Target ESP: тот, кого ударили
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide()) {
                TargetManager.set(entity);
                ModuleManager.onAttack(entity);
            }
            return InteractionResult.PASS;
        });

        LOGGER.info("Pepe Visual загружен: {} модулей", ModuleManager.MODULES.size());
    }
}

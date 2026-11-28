package ru.visual.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
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
import ru.visual.client.module.impl.Waypoints;
import ru.visual.client.util.TargetManager;

public class VisualClient implements ClientModInitializer {
    public static final String MOD_ID = "pepevisual";
    public static final Logger LOGGER = LoggerFactory.getLogger("PepeVisual");

    public static KeyMapping guiKey;
    public static KeyMapping zoomKey;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();

        guiKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pepevisual.gui", GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));
        zoomKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pepevisual.zoom", GLFW.GLFW_KEY_C, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            // несколько нажатий за тик - одно меню; поверх чата/инвентаря/таблички меню не открываем
            boolean openGui = false;
            while (guiKey.consumeClick()) openGui = true;
            if (openGui && mc.screen == null) mc.setScreen(new ClickGuiScreen());
            ru.visual.client.util.Aim.tick();
            TargetManager.tick(mc);
            ru.visual.client.util.HeldTracker.tick(mc);
            ModuleManager.tick(mc);
        });

        // 3D-отрисовка модулей (круги, коробки, стрелки)
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(ModuleManager::renderWorld);

        // Сломанный блок: посчитать в статистике сессии
        ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
            ru.visual.client.module.impl.SessionStats.onBlockBroken();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ModuleManager.shutdown());

        // Наш HUD / ESP (рисуется поверх ванильного HUD)
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.SUBTITLES,
                Identifier.fromNamespaceAndPath(MOD_ID, "modules"),
                ModuleManager::renderHud);

        // Клиентские команды: /pvwp (метки)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Waypoints.registerCommands(dispatcher));

        // NoRender: скрытие ванильных элементов экрана
        NoRender.registerHudReplacements();

        // Цель для Target HUD / Target ESP: тот, кого ударили
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide()) {
                TargetManager.hit(entity);
                ModuleManager.onAttack(entity);
            }
            return InteractionResult.PASS;
        });

        LOGGER.info("Pepe Visual загружен: {} модулей", ModuleManager.MODULES.size());
    }
}

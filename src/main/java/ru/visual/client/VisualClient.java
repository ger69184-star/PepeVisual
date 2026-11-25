package ru.visual.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import ru.visual.client.module.impl.Waypoints;
import ru.visual.client.module.impl.ChestSorter;
import ru.visual.client.module.impl.ShulkerPreview;
import ru.visual.client.util.ContainerMemory;
import ru.visual.client.util.TargetManager;

public class VisualClient implements ClientModInitializer {
    public static final String MOD_ID = "pepevisual";
    public static final Logger LOGGER = LoggerFactory.getLogger("PepeVisual");

    public static KeyMapping guiKey;
    public static KeyMapping zoomKey;
    public static KeyMapping freeLookKey;
    public static KeyMapping buildConfirm;
    public static KeyMapping buildRotate;
    public static KeyMapping buildCancel;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();

        guiKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pepevisual.gui", GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));
        zoomKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pepevisual.zoom", GLFW.GLFW_KEY_C, KeyMapping.Category.MISC));

        freeLookKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pepevisual.freelook", GLFW.GLFW_KEY_LEFT_ALT, KeyMapping.Category.MISC));

        // Режим строительства (BuildHelper)
        buildConfirm = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pepevisual.build.confirm", GLFW.GLFW_KEY_G, KeyMapping.Category.MISC));
        buildRotate = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pepevisual.build.rotate", GLFW.GLFW_KEY_R, KeyMapping.Category.MISC));
        buildCancel = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.pepevisual.build.cancel", GLFW.GLFW_KEY_H, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (guiKey.consumeClick()) {
                mc.setScreen(new ClickGuiScreen());
            }
            TargetManager.tick(mc);
            ContainerMemory.tick(mc);
            ModuleManager.tick(mc);
            // нажатия клавиш стройки, когда BuildHelper выключен, не копим
            while (buildConfirm.consumeClick()) {}
            while (buildRotate.consumeClick()) {}
            while (buildCancel.consumeClick()) {}
        });

        // 3D-отрисовка модулей (круги, коробки, стрелки)
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(ModuleManager::renderWorld);

        // Кнопки сортировки в сундуках и превью шалкеров в инвентаре
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            ChestSorter.onScreenInit(client, screen);
            ScreenEvents.afterRender(screen).register((s, g, mx, my, d) -> ShulkerPreview.onScreenRender(s, g, mx, my));
        });

        // Память содержимого сундуков: запоминаем, по какому блоку кликнули
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide()) ContainerMemory.onUseBlock(hit.getBlockPos());
            return InteractionResult.PASS;
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

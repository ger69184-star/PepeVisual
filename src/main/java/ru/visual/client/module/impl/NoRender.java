package ru.visual.client.module.impl;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import ru.visual.client.module.Category;
import ru.visual.client.module.Config;
import ru.visual.client.module.Module;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.setting.BoolSetting;

import java.util.function.BooleanSupplier;

/**
 * NoRender: убирает лишнее с экрана. Каждая галочка - отдельный элемент.
 * Элементы HUD скрываются через Fabric HUD API, оверлеи/частицы - через миксины.
 */
public class NoRender extends Module implements Config.DefaultOn {
    // Оверлеи в мире (миксины)
    public final BoolSetting fire = add(new BoolSetting("Огонь на экране", "Убирает пламя перед глазами, когда ты горишь.", true));
    public final BoolSetting water = add(new BoolSetting("Вода на экране", "Убирает синий оверлей под водой.", true));
    public final BoolSetting inWall = add(new BoolSetting("Тёмный экран в блоке", "Убирает затемнение, когда голова внутри блока.", true));
    public final BoolSetting hurtCam = add(new BoolSetting("Тряска при уроне", "Убирает наклон и тряску камеры при получении урона.", true));
    public final BoolSetting totem = add(new BoolSetting("Анимация тотема", "Убирает анимацию тотема бессмертия на весь экран.", false));
    public final BoolSetting particles = add(new BoolSetting("Частицы", "Полностью отключает частицы (дым, искры, зелья).", false));
    public final BoolSetting weather = add(new BoolSetting("Дождь и гроза", "Убирает дождь, снег и грозу на экране (серверная погода остаётся).", false));

    // Элементы HUD
    public final BoolSetting overlays = add(new BoolSetting("Оверлеи (тыква, портал, виньетка)", "Убирает тыкву на голове, эффект портала, виньетку, рыхлый снег и подзорную трубу.", true));
    public final BoolSetting bossBars = add(new BoolSetting("Боссбары", "Скрывает полосы здоровья боссов и событий сверху.", true));
    public final BoolSetting scoreboard = add(new BoolSetting("Скорборд", "Скрывает таблицу очков справа (на серверах там обычно реклама).", true));
    public final BoolSetting titles = add(new BoolSetting("Титры", "Скрывает крупные титры по центру экрана.", false));
    public final BoolSetting actionBar = add(new BoolSetting("Экшен-бар", "Скрывает надпись над хотбаром.", false));
    public final BoolSetting heldName = add(new BoolSetting("Название предмета", "Скрывает название предмета, всплывающее над хотбаром.", true));
    public final BoolSetting armorBar = add(new BoolSetting("Полоска брони", "Скрывает иконки брони над хотбаром.", false));
    public final BoolSetting foodBar = add(new BoolSetting("Полоска голода", "Скрывает иконки сытости.", false));
    public final BoolSetting xpLevel = add(new BoolSetting("Уровень опыта", "Скрывает число уровня опыта над хотбаром.", false));

    public NoRender() {
        super("NoRender", "Убирает с экрана всё лишнее: огонь, воду, тряску, частицы, боссбары, скорборд, титры и прочее.", Category.NORENDER);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (weather.get() && mc.level != null) {
            mc.level.setRainLevel(0f);
            mc.level.setThunderLevel(0f);
        }
    }

    /** Удобная проверка для миксинов. */
    public static boolean hides(BoolSetting s) {
        NoRender nr = ModuleManager.noRender;
        return nr != null && nr.isEnabled() && s.get();
    }

    public static void registerHudReplacements() {
        NoRender nr = ModuleManager.noRender;
        replace(VanillaHudElements.MISC_OVERLAYS, () -> nr.overlays.get());
        replace(VanillaHudElements.BOSS_BAR, () -> nr.bossBars.get());
        replace(VanillaHudElements.SCOREBOARD, () -> nr.scoreboard.get());
        replace(VanillaHudElements.TITLE_AND_SUBTITLE, () -> nr.titles.get());
        replace(VanillaHudElements.OVERLAY_MESSAGE, () -> nr.actionBar.get());
        replace(VanillaHudElements.HELD_ITEM_TOOLTIP, () -> nr.heldName.get());
        replace(VanillaHudElements.ARMOR_BAR, () -> nr.armorBar.get());
        replace(VanillaHudElements.FOOD_BAR, () -> nr.foodBar.get());
        replace(VanillaHudElements.EXPERIENCE_LEVEL, () -> nr.xpLevel.get());

        // Свой прицел рисуется ВМЕСТО ванильного - на его месте и при тех же условиях (не от 3-го лица и т.д.)
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, delta) -> {
            CustomCrosshair c = ModuleManager.crosshair;
            if (c != null && c.isEnabled()) {
                Minecraft mc = Minecraft.getInstance();
                if (!mc.options.hideGui) c.renderInPlace(graphics, delta);
                return;
            }
            original.render(graphics, delta);
        });
    }

    private static void replace(Identifier id, BooleanSupplier hide) {
        HudElementRegistry.replaceElement(id, original -> (graphics, delta) -> {
            NoRender nr = ModuleManager.noRender;
            if (nr != null && nr.isEnabled() && hide.getAsBoolean()) return;
            original.render(graphics, delta);
        });
    }
}

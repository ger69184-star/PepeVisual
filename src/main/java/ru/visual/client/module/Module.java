package ru.visual.client.module;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import ru.visual.client.VisualClient;
import ru.visual.client.setting.Setting;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name;
    public final String description;
    public final Category category;
    public final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;

    /** Клавиша включения (регистрируется в ModuleManager, меняется в меню клиента). */
    public KeyMapping bind;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    /**
     * Переключатель "Базовый / Продвинутый" для помощников и подстройка продвинутого режима.
     * Вызывать ПЕРВЫМ полем модуля, чтобы переключатель был наверху списка настроек.
     */
    protected ru.visual.client.util.Human.Tune humanTune(boolean camera, boolean breaks) {
        ru.visual.client.util.Human.Tune t = new ru.visual.client.util.Human.Tune();
        t.mode = add(new ru.visual.client.setting.ModeSetting("Поведение",
                "Базовый - как раньше: быстро и точно, камера сразу смотрит на блок, клики без пауз. "
                        + "Продвинутый - как живой игрок: плавная камера, обычные нажатия, разные паузы (меньше вопросов от античита).",
                ru.visual.client.util.Human.Tune.ADVANCED, ru.visual.client.util.Human.Tune.BASIC, ru.visual.client.util.Human.Tune.ADVANCED));
        t.reaction = addIf(new ru.visual.client.setting.NumSetting("Реакция",
                "Продвинутый режим: множитель пауз реакции. Меньше 1 - быстрее (но менее естественно), больше 1 - спокойнее.",
                1.0, 0.3, 3.0, 0.1), t::advanced);
        if (camera) {
            t.camera = addIf(new ru.visual.client.setting.NumSetting("Скорость камеры",
                    "Продвинутый режим: как быстро помощник поворачивает камеру. 1 - как обычный игрок, 2 - резко, 0.5 - очень плавно.",
                    1.0, 0.4, 2.5, 0.1), t::advanced);
            t.yieldMouse = addIf(new ru.visual.client.setting.BoolSetting("Уступать мыши",
                    "Продвинутый режим: если ты сам двигаешь мышь, помощник ждёт и не тянет камеру.", true), t::advanced);
        }
        if (breaks) {
            t.breaks = addIf(new ru.visual.client.setting.BoolSetting("Перерывы",
                    "Продвинутый режим: после 10-25 действий делать короткий перерыв (0.6-2 сек), как живой игрок.", true), t::advanced);
        }
        return t;
    }

    /** Настройка, которая видна в меню только при условии (значение всё равно сохраняется в конфиг). */
    protected <S extends Setting<?>> S addIf(S setting, java.util.function.BooleanSupplier visible) {
        setting.visible = visible;
        return add(setting);
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (settingsOnly()) value = true;
        if (value == enabled) return;
        enabled = value;
        try {
            if (value) onEnable(); else onDisable();
        } catch (Throwable t) {
            VisualClient.LOGGER.error("Ошибка в модуле {} ({})", name, value ? "enable" : "disable", t);
        }
        ModuleManager.onToggled(this);
    }

    /**
     * Используется при загрузке конфига (в момент запуска игры: мира и настроек игры ещё нет,
     * поэтому onEnable не вызывается - модули сами подхватывают состояние в onTick).
     */
    public void setEnabledSilently(boolean value) {
        this.enabled = value || settingsOnly();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    protected void onEnable() {}
    protected void onDisable() {}

    /** Включена ли функция при первом запуске (нет конфига). */
    public boolean defaultEnabled() {
        return settingsOnly() || this instanceof Config.DefaultOn;
    }

    /** Карточка только с настройками (без переключателя, всегда включена) - например, настройки меню. */
    public boolean settingsOnly() {
        return false;
    }

    /** true - onTick вызывается и в главном меню (когда мира нет). */
    public boolean tickOutsideWorld() {
        return false;
    }

    /** Каждый клиентский тик (только когда модуль включён и игрок в мире, см. tickOutsideWorld). */
    public void onTick(Minecraft mc) {}

    /** Каждый кадр при отрисовке HUD (только когда модуль включён). */
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {}

    /** Каждый кадр при отрисовке мира (3D-линии/коробки через util.R3D). */
    public void onWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx, float partialTick, Minecraft mc) {}

    /**
     * Сменился мир: выход с сервера, смена измерения (Overworld / Nether / End), переход между серверами и лобби
     * (на FunTime мир меняется без смены измерения). Вызывается у всех модулей, даже выключенных, - нужно забыть
     * всё, что привязано к старому миру (позиции, существа, начатые действия). Сам модуль при этом не выключается.
     */
    public void onWorldChange(Minecraft mc) {}

    /** Когда ты ударил существо (вызывается только у включённых модулей). */
    public void onAttack(Entity target) {}

    /** При закрытии игры: вернуть настройки игры в исходное состояние. */
    public void onShutdown() {}

    /** Короткая подпись в списке модулей. */
    public String info() {
        return null;
    }
}

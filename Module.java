package ru.visual.client.module;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
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

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        if (value) onEnable(); else onDisable();
    }

    /** Используется при загрузке конфига. */
    public void setEnabledSilently(boolean value) {
        this.enabled = value;
        if (value) onEnable();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    protected void onEnable() {}
    protected void onDisable() {}

    /** Каждый клиентский тик (только когда модуль включён). */
    public void onTick(Minecraft mc) {}

    /** Каждый кадр при отрисовке HUD (только когда модуль включён). */
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {}

    /** Когда ты ударил существо (вызывается только у включённых модулей). */
    public void onAttack(Entity target) {}

    /** При закрытии игры: вернуть настройки игры в исходное состояние. */
    public void onShutdown() {}

    /** Короткая подпись в списке модулей. */
    public String info() {
        return null;
    }
}

package ru.visual.client.setting;

/** Базовая настройка модуля. У каждой есть описание, которое показывается при наведении курсора. */
public abstract class Setting<T> {
    public final String name;
    public final String description;
    protected T value;
    /** Значение по умолчанию (для сброса ПКМ в меню). */
    public final T def;

    protected Setting(String name, String description, T value) {
        this.name = name;
        this.description = description;
        this.value = value;
        this.def = value;
    }

    /** Скрытая настройка: сохраняется в конфиг, но в меню не показывается (например, позиция HUD - её двигают мышью). */
    public boolean hidden;

    /** Показывать только при условии (например, настройки "Продвинутого" режима). null - всегда. */
    public java.util.function.BooleanSupplier visible;

    /** Видна ли настройка в меню сейчас. */
    public boolean shown() {
        return !hidden && (visible == null || visible.getAsBoolean());
    }

    public T get() {
        return value;
    }
}

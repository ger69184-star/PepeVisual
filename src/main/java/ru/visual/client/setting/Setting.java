package ru.visual.client.setting;

/** Базовая настройка модуля. У каждой есть описание, которое показывается при наведении курсора. */
public abstract class Setting<T> {
    public final String name;
    public final String description;
    protected T value;

    protected Setting(String name, String description, T value) {
        this.name = name;
        this.description = description;
        this.value = value;
    }

    public T get() {
        return value;
    }
}

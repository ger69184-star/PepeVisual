package ru.visual.client.module;

public enum Category {
    VISUAL("Visual"),
    HELPERS("Helper"),
    MOVEMENT("Movement"),
    HUD("HUD"),
    NORENDER("NoRender"),
    UTILITY("Utility"),
    OPTIMIZATION("Optimization"),
    /** Оформление и настройки самого меню: масштаб, тема, цвета, язык. */
    MENU("Menu");

    public final String title;

    Category(String title) {
        this.title = title;
    }
}

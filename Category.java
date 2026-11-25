package ru.visual.client.module;

public enum Category {
    VISUAL("Visual"),
    HELPERS("Helper"),
    MOVEMENT("Movement"),
    HUD("HUD"),
    NORENDER("NoRender"),
    UTILITY("Utility"),
    OPTIMIZATION("Optimization");

    public final String title;

    Category(String title) {
        this.title = title;
    }
}

package ru.visual.client.setting;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, String description, boolean def) {
        super(name, description, def);
    }

    public void set(boolean v) {
        this.value = v;
    }

    public void toggle() {
        this.value = !this.value;
    }
}

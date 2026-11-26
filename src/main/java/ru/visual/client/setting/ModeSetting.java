package ru.visual.client.setting;

/** Выбор из списка режимов (в меню открывается выпадающий список). */
public class ModeSetting extends Setting<String> {
    public final String[] modes;
    private int index;

    public ModeSetting(String name, String description, String def, String... modes) {
        super(name, description, def);
        this.modes = modes;
        set(def);
    }

    public void set(String mode) {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(mode)) {
                index = i;
                value = modes[i];
                return;
            }
        }
    }

    public void cycle(int dir) {
        index = ((index + dir) % modes.length + modes.length) % modes.length;
        value = modes[index];
    }

    public int index() {
        return index;
    }

    public boolean is(String mode) {
        return value.equals(mode);
    }
}

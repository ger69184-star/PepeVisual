package ru.visual.client.setting;

public class NumSetting extends Setting<Double> {
    public final double min, max, step;

    public NumSetting(String name, String description, double def, double min, double max, double step) {
        super(name, description, def);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public void set(double v) {
        double c = Math.max(min, Math.min(max, v));
        if (step > 0) c = Math.round(c / step) * step;
        this.value = Math.max(min, Math.min(max, c));
    }

    public int asInt() {
        return (int) Math.round(value);
    }

    public float asFloat() {
        return value.floatValue();
    }

    public String display() {
        if (step >= 1) return String.valueOf(asInt());
        // столько знаков, сколько нужно шагу: 0.1 -> "1.5", 0.05 -> "0.75"
        int digits = step >= 0.1 - 1e-9 ? 1 : 2;
        return String.format(java.util.Locale.ROOT, "%." + digits + "f", value);
    }

    public void reset() {
        set(def);
    }
}

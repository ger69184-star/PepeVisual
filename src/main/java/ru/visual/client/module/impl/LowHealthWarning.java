package ru.visual.client.module.impl;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import ru.visual.client.module.Category;
import ru.visual.client.module.Module;
import ru.visual.client.setting.BoolSetting;
import ru.visual.client.setting.NumSetting;
import ru.visual.client.util.Draw;

/** Красная пульсирующая рамка по краям экрана и звук, когда здоровья мало. */
public class LowHealthWarning extends Module {
    private final NumSetting hearts = add(new NumSetting("Порог (сердца)", "При скольких сердцах и меньше предупреждать.", 4, 1, 10, 0.5));
    private final BoolSetting vignette = add(new BoolSetting("Красные края", "Пульсирующая красная рамка по краям экрана.", true));
    private final BoolSetting text = add(new BoolSetting("Надпись", "Надпись \"Мало здоровья\" над хотбаром.", true));
    private final BoolSetting sound = add(new BoolSetting("Звук", "Короткий звук, когда здоровье падает ниже порога.", true));

    private boolean low;

    public LowHealthWarning() {
        super("LowHealth", "Предупреждение о малом здоровье: красные края экрана, надпись и звук.", Category.HUD);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        boolean now = !mc.player.isCreative() && !mc.player.isSpectator() && mc.player.isAlive()
                && mc.player.getHealth() + mc.player.getAbsorptionAmount() <= hearts.get() * 2;
        if (now && !low && sound.get()) mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
        low = now;
    }

    @Override
    public void onHud(GuiGraphics g, DeltaTracker delta, Minecraft mc) {
        if (!low || mc.options.hideGui) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0);
        if (vignette.get()) {
            int a = (int) (0x50 + 0x50 * pulse);
            int edge = Math.max(18, Math.min(w, h) / 8);
            int c = (a << 24) | 0xFF0000;
            g.fillGradient(0, 0, w, edge, c, 0x00FF0000);
            g.fillGradient(0, h - edge, w, h, 0x00FF0000, c);
            // боковые: горизонтального градиента нет, рисуем полосками
            int steps = 12;
            for (int i = 0; i < steps; i++) {
                int ca = (int) (a * (1.0 - i / (double) steps));
                int x1 = edge * i / steps, x2 = edge * (i + 1) / steps;
                g.fill(x1, 0, x2, h, (ca << 24) | 0xFF0000);
                g.fill(w - x2, 0, w - x1, h, (ca << 24) | 0xFF0000);
            }
        }
        if (text.get()) {
            float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            String s = "Мало здоровья: " + String.format(java.util.Locale.ROOT, "%.1f", hp / 2f) + " сердец";
            int col = Draw.withAlpha(0xFF5050, (int) (180 + 75 * pulse));
            Draw.textCentered(g, s, w / 2, h / 2 - 28, col); // над прицелом: внизу уже броня и хотбар
        }
    }
}
